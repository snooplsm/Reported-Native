import axios from "axios";
import { AsyncStorage } from "react-native";
import { isSignedIn } from "./Auth";
import { USER_KEY } from "./Auth";
import Amplify, { Storage } from "aws-amplify";
import moment from "moment";
import { Platform } from "react-native";
import { Constants } from "expo";

Amplify.configure({
  Auth: {
    identityPoolId: "us-east-1:ccbbb41a-4490-4b85-af8d-a047aabeec5d", //REQUIRED - Amazon Cognito Identity Pool ID
    region: "us-east-1" // REQUIRED - Amazon Cognito Region
  },
  Storage: {
    AWSS3: {
      bucket: "reportedcab" //REQUIRED -  Amazon S3 bucket
    }
  }
});

const ax = axios.create({
  baseURL: "http://localhost:8084/staging/"
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

export const uploadFile = (file, meta, success, error) => {
  console.log("uploadFile", file, meta, success, error);
  const data = {};
  return new Promise((resolve, reject) => {
    isSignedIn()
      .then(user => {
        data.user = user;
        console.log("we have user");
        return fetch(file.url);
      })
      .then(file => {
        console.log("making blob");
        return file.blob();
      })
      .then(blob => {
        console.log("blobby");
        const time = moment().format("YYYY_MM_DD_HH_mm_ss_SSS");
        const key = `${data.user.id}/${time}.jpg`;
        console.log("blob key", key);
        const metaData = Object.assign(
          {
            "User-Id": data.user.id,
            "File-Name": file.url,
            "Operating-System": Platform.OS,
            Device: Constants.deviceName,
            width: file.width,
            height: file.height,
            duration: file.duration,
            lat: file.lat,
            lng: file.lng,
            timeofimage: file.timeofimage
          },
          meta
        );
        console.log("meta data", meta);
        Storage.put(key, blob, {
          customPrefix: {
            public: "uploads/"
          },
          level: "public",
          metadata: metaData,
          contentType: "image/jpeg"
        })
          .then(res => {
            console.log("success", res);
            resolve(res);
            success(file, res);
          })
          .catch(e => {
            console.log("there was an error");
            console.log(e);
            reject(e);
            error(e);
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

  reports: filter => {
    if (filter == null) {
      return ax.get("/reports");
    } else {
      console.log("reports?", JSON.stringify(filter));
      return ax.get(`/reports?filter=${JSON.stringify(filter)}`);
    }
  }
};
