import axios from "axios";
import { isSignedIn } from "./Auth";
import { AsyncStorage } from "react-native";
import { USER_KEY } from "./Auth";
import Amplify, { Storage } from "aws-amplify";
import moment from "moment";
import { Platform } from "react-native";
import Constants from "expo-constants";
import * as ImageManipulator from "expo-image-manipulator";
import * as FileSystem from "expo-file-system";

const apiUrl = {
  dev: "https://reported-stats.herokuapp.com/staging/",
  staging: "https://reported-stats.herokuapp.com/staging/",
  prod: "https://reported-stats.herokuapp.com/prod/"
};

function getApiUrl() {
  if (__DEV__) {
    return apiUrl.dev;
  }
  const channel = Constants.manifest.releaseChannel;
  if (channel === undefined) return apiUrl.dev;
  if (channel.indexOf("prod") !== -1) return apiUrl.prod;
  if (channel.indexOf("staging") !== -1) return apiUrl.staging;
}

function getBucketUrl() {
  if (__DEV__) {
    return "reportedcab-stg";
  }
  const channel = Constants.manifest.releaseChannel;
  if (channel === undefined) return "reportedcab-stg";
  if (channel.indexOf("prod") !== -1) return "reportedcab";
  if (channel.indexOf("staging") !== -1) return "reportedcab-stg";
}

const BASE_URL = getApiUrl();

const BUCKET = getBucketUrl();

Amplify.configure({
  Auth: {
    identityPoolId: "us-east-1:ccbbb41a-4490-4b85-af8d-a047aabeec5d", //REQUIRED - Amazon Cognito Identity Pool ID
    region: "us-east-1" // REQUIRED - Amazon Cognito Region
  },
  Storage: {
    AWSS3: {
      bucket: BUCKET
    }
  }
});

const ax = axios.create({
  baseURL: BASE_URL
});

const openAx = axios.create({
  baseURL: "https://api.openalpr.com/",
  params: {
    secret_key: "sk_63c7b9750e41acfadc721f90",
    country: "us"
  }
});

ax.interceptors.request.use(
  config => {
    return new Promise((resolve, eject) => {
      isSignedIn()
        .then(user => {
          if (user) {
            config.headers.common["X-User-Id"] = user.id;
            config.headers.common["X-Session-Token"] = user.sessionToken;
            config.headers.common["X-Operating-System"] = Platform.OS;
            config.headers.comming["X-Build-Number"] =
              Constants.nativeBuildVersion;
          }
          resolve(config);
        })
        .catch(err => resolve(config));
    });
  },
  function(error) {
    // Do something with request error
    return Promise.reject(error);
  }
);

export const uploadFile = (file, extra) => {
  return new Promise((resolve, reject) => {
    const data = {};
    isSignedIn()
      .then(user => {
        data.user = user;
        if (file.type === "image") {
          return ImageManipulator.manipulateAsync(file.url || file.uri, [], {
            compress: 1.0,
            format: ImageManipulator.SaveFormat.JPEG
          });
        } else {
          return file;
        }
      })
      .catch(e => {
        console.log(e);
        throw e;
      })
      .then(fc => {
        data.fc = fc;
        return FileSystem.getInfoAsync(fc.uri || fc.url, {
          md5: true
        });
      })
      .then(fc => {
        data.fc.md5 = fc.md5;
        return urlToBlob(data.fc.uri || data.fc.url);
      })
      .then(blob => {
        const time = moment().format("YYYY_MM_DD_HH_mm_ss_SSS");
        let ext = data.fc.uri || data.fc.url;
        ext =
          ext.lastIndexOf(".") != -1 &&
          ext.lastIndexOf(".") != ext.length - 1 &&
          ext.substring(ext.lastIndexOf(".") + 1).toLowerCase();
        const key = `${data.user.id}/${time}.${ext}`;
        const metaData = Object.assign({
          "User-Id": data.user.id,
          "File-Name": data.fc.uri || data.fc.url,
          "Operating-System": Platform.OS,
          Device: Constants.deviceName,
          width: (data.fc.width ?? -1).toString(),
          height: (data.fc.height ?? -1).toString(),
          duration: (file.duration ?? -1).toString(),
          lat: (file.lat ?? 0.0).toString(),
          lng: (file.lng ?? 0.0).toString(),
          timeofimage: (file.takenAt && file.takenAt.toString()) ?? "-1"
        });
        data.size = blob.size;
        data.meta = metaData;
        let contentType = null;
        if (file.type === "image" || ext === "jpg") {
          contentType = "image/jpeg";
        } else {
          contentType = "video/mp4";
        }
        const callback = extra && extra.listener;
        Storage.put(key, blob, {
          customPrefix: {
            public: "uploads/"
          },
          level: "public",
          progressCallback: callback,
          metadata: metaData,
          contentType: contentType
        }).then(res => {
          resolve({
            url: `https://${BUCKET}.s3.amazonaws.com/uploads/${res.key}`,
            meta: data.meta,
            width: data.fc.width,
            height: data.fc.height,
            duration: file.duration,
            etag: data.fc.md5,
            size: data.size,
            takenAt: file.takenAt
          });
        });
      });
  });
};

class UserPromise extends Promise {
  constructor(res) {
    super((resolve, reject) => {
      AsyncStorage.setItem(
        USER_KEY,
        JSON.stringify(res.data),
        (error, result) => {
          resolve(res);
        }
      );
    });
  }
}

const CHAR_CODE = Constants.installationId.charCodeAt(0);

function getKey() {
  if (CHAR_CODE < 57) {
    return "sk_63c7b9750e41acfadc721f90";
  }
  return "sk_9d99ad00460f1109de48c8ad";
}

const ALPR_KEY = getKey();

const ALPR_MD5 = "alpr.result";

export const alpr = {
  recognize: file => {
    const temp = {};

    return FileSystem.getInfoAsync(file.uri || file.url, {
      md5: true
    })
      .then(info => {
        const { md5 } = info;
        temp.key = `${ALPR_MD5}.${md5}`;
        return AsyncStorage.getItem(temp.key);
      })
      .then(response => {
        if (response) {
          return JSON.parse(response);
        } else {
          const url = `https://api.openalpr.com/v2/recognize?country=us&secret_key=${ALPR_KEY}`;
          const form = new FormData();
          form.append("image", {
            name: "image",
            type: "image/jpeg",
            uri: file.uri || file.url
          });
          let options = {
            method: "POST",
            body: form,
            headers: {
              Accept: "application/json",
              "Content-Type": "multipart/form-data"
            }
          };
          return fetch(url, options)
            .then(result => result.json())
            .then(json => {
              AsyncStorage.setItem(temp.key, JSON.stringify(json));
              return json;
            });
        }
      });
  }
};

const urlToBlob = url =>
  new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.onerror = reject;
    xhr.onreadystatechange = () => {
      if (xhr.readyState === 4) {
        resolve(xhr.response);
      }
    };
    xhr.open("GET", url);
    xhr.responseType = "blob"; // convert type
    xhr.send();
  });

export const finds = (address, key) => {
  return address
    .filter(x => x.types.includes(key))
    .map(x => x.short_name)
    .shift();
};

export const geocode = address => {
  if (!address) {
    return Promise.reject(`illegal address ${address}`);
  }
  const key = `location1.${address}`;
  return AsyncStorage.getItem(key).then(item => {
    if (item) {
      return JSON.parse(item);
    }
    const url = `https://maps.googleapis.com/maps/api/geocode/json?key=AIzaSyDiBYFqZLwPsNkMbRNqr1_63h-w9fcZNVM&address=${encodeURIComponent(
      address
    )}`;
    return fetch(url)
      .then(res => res.json())
      .then(data => {
        let item = null;
        if (data.results && data.results.length > 0) {
          item = data.results[0];
        }
        if (item) {
          AsyncStorage.setItem(key, JSON.stringify(item));
        }
        return item;
      });
  });
};

export const reverseGeocode = location => {
  if (!location || !location.lat || !location.lng) {
    return Promise.reject(
      `illegal location ${location == null ? null : JSON.stringify(location)}`
    );
  }
  const key = `location.${location.lat}.${location.lng}`;
  return AsyncStorage.getItem(key).then(item => {
    if (item) {
      return JSON.parse(item);
    }
    const url = `https://maps.googleapis.com/maps/api/geocode/json?key=AIzaSyDiBYFqZLwPsNkMbRNqr1_63h-w9fcZNVM&latlng=${location.lat.toFixed(
      3
    )},${location.lng.toFixed(3)}&rankby=distance`;
    return fetch(url)
      .then(res => res.json())
      .then(data => {
        AsyncStorage.setItem(key, JSON.stringify(data));
        return data;
      });
  });
};

export const api = {
  register: body => {
    return ax.post(`/register`, body).then(res => {
      return new UserPromise(res);
    });
  },

  forgotPassword: forgot => {
    return ax.post("/forgot_password", forgot);
  },

  changePassword: password => {
    return ax.post(`/change_password`, {
      password: password
    });
  },

  updateUser: user => {
    return ax.put("/user/update", user);
  },

  login: (username, password) => {
    return ax
      .post("/login", {
        username,
        password
      })
      .then(res => {
        return new UserPromise(res);
      });
  },

  report: report => {
    return ax.put("/report", report);
  },

  deleteReport: reportId => {
    return ax.delete(`/report/delete/${reportId}`, {});
  },

  reports: (filter, _skip) => {
    const skip = _skip ?? 0;
    const f2 =
      filter &&
      Object.assign(Object.assign({}, filter), {
        when: filter.when && filter.when.allDates,
        complaints: filter.complaints.map(x => x.name)
      });
    if (filter == null) {
      return ax.get(`/reports?skip=${skip}`);
    } else {
      console.log("filter", f2);
      return ax.get(`/reports?skip=${skip}&filter=${JSON.stringify(f2)}`);
    }
  }
};
