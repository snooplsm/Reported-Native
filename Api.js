import axios from "axios";
import { AsyncStorage } from "react-native";
import { isSignedIn } from "./Auth";
import { USER_KEY } from "./Auth";
import Amplify, { Storage } from "aws-amplify";
import moment from "moment";
import { Platform } from "react-native";
import { Constants, ImageManipulator, FileSystem } from "expo";

const BUCKET = "reportedcab";

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

const BASE_URL = "http://192.168.43.16:8084/staging/";

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

export const uploadFile = file => {
  return new Promise((resolve, reject) => {
    const data = {};
    isSignedIn()
      .then(user => {
        data.user = user;
        console.log("we have user");
        return ImageManipulator.manipulateAsync(file.url, null, {
          compress: 0.3
        });
      })
      .then(fc => {
        data.fc = fc;
        return urlToBlob(fc.uri);
      })
      .then(blob => {
        const time = moment().format("YYYY_MM_DD_HH_mm_ss_SSS");
        const key = `${data.user.id}/${time}.jpg`;
        console.log("blob key", key);
        const metaData = Object.assign({
          "User-Id": data.user.id,
          "File-Name": data.fc.uri,
          "Operating-System": Platform.OS,
          Device: Constants.deviceName,
          width: (data.fc.width ?? -1).toString(),
          height: (data.fc.height ?? -1).toString(),
          duration: (file.duration ?? -1).toString(),
          lat: (file.lat ?? 0.0).toString(),
          lng: (file.lng ?? 0.0).toString(),
          timeofimage: file.timeofimage ?? "-1"
        });
        data.size = blob.size;
        data.meta = metaData;
        Storage.put(key, blob, {
          customPrefix: {
            public: "uploads/"
          },
          level: "public",
          metadata: metaData,
          contentType: "image/jpeg"
        }).then(res => {
          console.log("success", res);
          resolve({
            url: `https://${BUCKET}.s3.amazonaws.com/${res.key}`,
            meta: data.meta,
            width: data.fc.width,
            height: data.fc.height,
            duration: file.duration,
            size: data.size
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

export const alpr = {
  recognize: file => {
    const url =
      "https://api.openalpr.com/v2/recognize?country=us&secret_key=sk_63c7b9750e41acfadc721f90";
    const form = new FormData();
    form.append("image", file);
    let options = {
      method: "POST",
      body: form,
      headers: {
        Accept: "application/json",
        "Content-Type": "multipart/form-data"
      }
    };
    return fetch(url, options);
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

export const api = {
  register: email => {
    return ax
      .post(`/register`, {
        email: email
      })
      .then(res => {
        return new UserPromise(res);
      });
  },

  // upload: (fileJson, meta) => {
  //   console.log("upload fileJson");
  //   FileSystem.getInfoAsync(fileJson.url, { md5: true }).then(info => {
  //     console.log(info);
  //     return info;
  //   });
  //   return urlToBlob(fileJson.url).then(file => {
  //     console.log("fetch upload", BASE_URL);
  //     console.log(file);
  //     return fetch(`${BASE_URL}/upload`, {
  //       method: "PUT",
  //       body: file
  //     });
  //   });
  // },

  changePassword: password => {
    return ax.post(`/change_password`, {
      password: password
    });
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
    console.log("put report", report);
    return ax.put("/report", report);
  },

  reports: filter => {
    if (filter == null) {
      return ax.get("/reports");
    } else {
      console.log("reports?", JSON.stringify(filter));
      return ax.get(`/reports?filter=${JSON.stringify(filter)}`);
    }
  }
};
