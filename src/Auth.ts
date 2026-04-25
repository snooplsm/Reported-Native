import AsyncStorage from "@react-native-async-storage/async-storage";

export const USER_KEY = "user";

export type SignedInUser = {
  id: string;
  sessionToken: string;
  [key: string]: unknown;
};

export const isSignedIn = (): Promise<SignedInUser | false> => {
  return new Promise((resolve, reject) => {
    AsyncStorage.getItem(USER_KEY)
      .then(res => {
        if (res !== null) {
          resolve(JSON.parse(res) as SignedInUser);
        } else {
          resolve(false);
        }
      })
      .catch(err => reject(err));
  });
};

export const signOut = (): Promise<true> => {
  return new Promise((resolve, reject) => {
    AsyncStorage.clear()
      .then(res => {
        resolve(true);
      })
      .catch(err => reject(err));
  });
};
