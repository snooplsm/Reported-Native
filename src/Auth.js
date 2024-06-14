import AsyncStorage from "@react-native-async-storage/async-storage";

export const USER_KEY = "user";

export const isSignedIn = () => {
  return new Promise((resolve, reject) => {
    AsyncStorage.getItem(USER_KEY)
      .then(res => {
        if (res !== null) {
          resolve(JSON.parse(res));
        } else {
          resolve(false);
        }
      })
      .catch(err => reject(err));
  });
};

export const signOut = () => {
  return new Promise((resolve, reject) => {
    AsyncStorage.clear()
      .then(res => {
        resolve(true);
      })
      .catch(err => reject(err));
  });
};
