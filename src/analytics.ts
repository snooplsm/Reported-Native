import {
  getAnalytics,
  logAppOpen,
  logEvent,
  logScreenView
} from "@react-native-firebase/analytics";

const analytics = getAnalytics();

const safeAnalyticsCall = (call: () => Promise<void>) => {
  call().catch(error => {
    console.warn("Firebase Analytics error", error);
  });
};

export const appAnalytics = {
  logAppOpen() {
    safeAnalyticsCall(() => logAppOpen(analytics));
  },

  logScreenView(screenName: string) {
    safeAnalyticsCall(() =>
      logScreenView(analytics, {
        screen_name: screenName,
        screen_class: screenName
      })
    );
  },

  logEvent(name: string, params?: Record<string, string | number | boolean | undefined>) {
    safeAnalyticsCall(() => logEvent(analytics, name, params));
  }
};
