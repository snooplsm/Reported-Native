import axios from 'axios'
import {AsyncStorage} from 'react-native';

const ax = axios.create({
  baseURL: "https://hidden-scrubland-12819.herokuapp.com"
});

const openAx = axios.create({
  baseURL: "https://api.openalpr.com/",
  params: {
    secret_key: 'sk_63c7b9750e41acfadc721f90',
    country: 'us'
  }
})

class UserPromise extends Promise {
  constructor(res) {
    super((resolve, reject) => {
      AsyncStorage.setItem('user', JSON.stringify(res.data), (error, result) => {
        resolve(res)
      })
    })
  }
}

export const alpr = {
  recognize: (file)=> {
    const url = 'https://api.openalpr.com/v2/recognize?country=us&secret_key=sk_63c7b9750e41acfadc721f90'
    const form = new FormData()
    form.append('image', file)
    let options = {
    method: 'POST',
    body: form,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'multipart/form-data',
    },
    };
    return fetch(url, options)
  }
}

export const api = {
  register: (email) => {
    return ax.post(`/register`, {
      email: email
    }).then(res=> {
      return new UserPromise(res)
    })
  },

  changePassword: (password) => {
    return ax.post(`/change_password`, {
      password: password
    })
  },

  login: (email, password) => {
    return ax.post('/login', {
      email: email,
      password: password
    }).then(res=> {
      return new UserPromise(res)
    })
  }
}

// ax.interceptors.request.use((config)=> {
//   return new Promise((resolve, reject) => {
//     config.headers.common['X-User-Id'] = userId
//     config.headers.common['X-User-Token'] = authToken
//     resolve(config)
//   })
// }, (error)=> {
//   return Promise.reject(error)
// })
