import axios from "axios";
import { AsyncStorage } from "react-native";
import { isSignedIn } from "./Auth";
import { USER_KEY } from "./Auth";

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
