import Constants from "expo-constants";

const extra = (Constants.expoConfig?.extra?.reported ?? {}) as Record<string, string | undefined>;

const readEnv = (primary: string | undefined, fallback: string | undefined) =>
  primary ?? fallback ?? "";

export const env = {
  awsAccessKeyId: readEnv(
    process.env.EXPO_PUBLIC_REPORTED_AWS_ACCESS_KEY_ID,
    extra.awsAccessKeyId ?? process.env.REPORTED_AWS_ACCESS_KEY_ID
  ),
  awsSecretAccessKey: readEnv(
    process.env.EXPO_PUBLIC_REPORTED_AWS_SECRET_ACCESS_KEY,
    extra.awsSecretAccessKey ?? process.env.REPORTED_AWS_SECRET_ACCESS_KEY
  ),
  googleMapsApiKey: readEnv(
    process.env.EXPO_PUBLIC_REPORTED_GOOGLE_MAPS_API_KEY,
    extra.googleMapsApiKey ?? process.env.REPORTED_GOOGLE_MAPS_API_KEY
  ),
  firebaseApiKey: readEnv(
    process.env.EXPO_PUBLIC_REPORTED_FIREBASE_API_KEY,
    extra.firebaseApiKey ?? process.env.REPORTED_FIREBASE_API_KEY
  )
};
