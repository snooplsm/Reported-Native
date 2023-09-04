import axios from "axios";
import { isSignedIn } from "./Auth";

import S3 from "aws-sdk/clients/s3";
import { Credentials } from "aws-sdk";

import AsyncStorage from "@react-native-async-storage/async-storage";
import { USER_KEY } from "./Auth";
import moment from "moment";
import { Platform } from "react-native";
import Constants from "expo-constants";
import { Notifications } from "expo";
import * as ImageManipulator from "expo-image-manipulator";
import * as FileSystem from "expo-file-system";

const apiUrl = {
    // dev: "https://reported-stats.herokuapp.com/prod/",
    dev: "https://reported.webabot.com/api/1/",
    staging: "https://reported-stats.herokuapp.com/staging/",
    prod: "https://reported-stats.herokuapp.com/prod/"
};

const access = new Credentials({
    accessKeyId: 'AKIAWXBK5VSMACYJ7YUY',
    secretAccessKey: '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',
});

const s3 = new S3({
    credentials: access,
    region: 'us-east-1',
    signatureVersion: 'v4',
});

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
                    const buildNumber =
                        Platform.OS === "android"
                            ? Constants.platform.android.versionCode
                            : Constants.manifest.ios.buildNumber;

                    if (user) {
                        config.headers.common["X-User-Id"] = user.id;
                        config.headers.common["X-Session-Token"] = user.sessionToken;
                        config.headers.common["X-Operating-System"] = Platform.OS;
                        config.headers.common["X-Build-Number"] = buildNumber ?? "-999";
                    }
                    resolve(config);
                })
                .catch(err => resolve(config));
        });
    },
    function (error) {
        // Do something with request error
        return Promise.reject(error);
    }
);

ax.interceptors.request.use(request => {
    console.log('Starting Request', JSON.stringify(request, null, 2))
    return request
})

ax.interceptors.response.use(response => {
    console.log('Response:', JSON.stringify(response, null, 2))
    return response
})

const uploadImageOnS3 = (key, contentType, file) => {
    return new Promise((resolve, reject) => {
        const s3bucket = new S3({
            accessKeyId: 'AKIAWXBK5VSMACYJ7YUY',
            secretAccessKey: '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',
            Bucket: BUCKET,
            signatureVersion: 'v4',
        });
        const params = {
            Bucket: `${BUCKET}/uploads`,
            Key: key,
            Acl: "public-read",
            Body: file,
            ContentType: contentType,
        };
        s3bucket.upload(params, (err, data) => {
            if (err) {
                reject('error in callback');
            }
            resolve(data.Location);
        });
    });
};

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
                throw e;
            })
            .then(fc => {
                const tmp = Object.assign({}, fc)
                data.fc = tmp;
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
                let ContentType = null;
                if (file.type === "image" || ext === "jpg") {
                    ContentType = "image/jpeg";
                } else if (file.type === "pdf" || ext === "pdf") {
                    ContentType = "application/pdf";
                } else {
                    ContentType = "video/mp4";
                }

                return uploadImageOnS3(key, ContentType, blob);
            })
            .then(dataLocation => {
                resolve({
                    url: dataLocation,
                    meta: data.meta,
                    width: data.fc.width,
                    height: data.fc.height,
                    duration: file.duration,
                    etag: data.fc.md5,
                    size: data.size,
                    takenAt: file.takenAt
                })
            }).catch(e => {
                reject(e)
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

export const precincts = () => {
    const key = "@nyc.precinct.01";
    return AsyncStorage.getItem(key).then(precincts => {
        if (precincts) {
            return JSON.parse(precincts);
        }
        return fetch(
            `https://raw.githubusercontent.com/snooplsm/nyc_neighborhood_polylines/master/nyc_precincts.json?date=${new Date().valueOf()}`
        )
            .then(result => result.json())
            .then(result => {
                const json = result;
                AsyncStorage.setItem(key, JSON.stringify(json));
                return json;
            });
    });
};

export const reverseGeocode = location => {
    const url = `https://maps.googleapis.com/maps/api/geocode/json?key=AIzaSyDiBYFqZLwPsNkMbRNqr1_63h-w9fcZNVM&latlng=${location.lat},${location.lng}&result_type=street_address&rankBy=distance`;
    return fetch(url)
        .then(res => res.json())
        .then(data => {
            return data;
        })
        .catch(e => {
            //
            //Here you can log/handle error messages from Google API
            //
            return Promise.reject('The location does not appear to be a valid street address.')
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
        return ax.put("/user/update", user).then(res => {
            return new UserPromise(res);
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

    registerToken: token => {
        const key = {};
        return isSignedIn()
            .then(user => {
                key.key = `@user.token1.${user && user.id}`;
                return AsyncStorage.getItem(key.key);
            })
            .then(_token => {
                if (_token !== token) {
                    return ax
                        .put("/user/register/token", {
                            token: token,
                            channels: ["general"]
                        })
                        .then(res => {
                            if (res.request.status / 100 === 2) {
                                AsyncStorage.setItem(key.key, token);
                            }
                            return res;
                        });
                } else {
                    return token;
                }
            });
    },

    report: report => {
        return ax.put("/report", report);
    },

    changeStatus: payload => {
        // console.log(payload);
        return ax.put("/report/change_status", payload);
    },

    deleteReport: reportId => {
        return ax.delete(`/report/delete/${reportId}`, {});
    },

    myReports: (filter, _skip) => {
        const skip = _skip ?? 0;
        const f2 =
            filter &&
            Object.assign(Object.assign({}, filter), {
                when: filter.when && filter.when.allDates,
                complaints: filter.complaints.map(x => x.name)
            });
        // console.log("filter", f2);
        if (filter == null) {
            return ax.get(`/reports/all?skip=${skip}`);
        } else {
            // console.log("filter", f2);
            return ax.get(`/reports?skip=${skip}&filter=${JSON.stringify(f2)}`);
        }
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
            // console.log("filter", f2);
            return ax.get(
                `/reports?skip=${skip}&filter=${encodeURIComponent(JSON.stringify(f2))}`
            );
        }
    },

    reportStats: () => {
        return ax.get(`/reports/aggregate`);
    }
};

export async function pushTokenNeedsRegisteringAsync() {
    const { status: existingStatus } = await Permissions.getAsync(
        Permissions.NOTIFICATIONS
    );
    let finalStatus = existingStatus;
    if (existingStatus !== "granted") {
        return true;
    }
    let user = await isSignedIn();
    if (!user) {
        return true;
    }
    let key = `@user.token1.${user && user.id}`;
    let token = await AsyncStorage.getItem(key);
    let token2 = await Notifications.getExpoPushTokenAsync();
    return token2 !== token;
}

export async function registerForPushNotificationsAsync() {
    const { status: existingStatus } = await Permissions.getAsync(
        Permissions.NOTIFICATIONS
    );
    let finalStatus = existingStatus;

    // only ask if permissions have not already been determined, because
    // iOS won't necessarily prompt the user a second time.
    if (existingStatus !== "granted") {
        // Android remote notification permissions are granted during the app
        // install, so this will only ask on iOS
        const { status } = await Permissions.askAsync(Permissions.NOTIFICATIONS);
        finalStatus = status;
    }

    // Stop here if the user did not grant permissions
    if (finalStatus !== "granted") {
        return new Error(`No Permission Granted. ${finalStatus}`);
    }

    // Get the token that uniquely identifies this device
    let token = await Notifications.getExpoPushTokenAsync();
    // console.log("token2", token);
    // POST the token to your backend server from where you can retrieve it to send push notifications.
    const result = await api.registerToken(token);
    return result;
}
