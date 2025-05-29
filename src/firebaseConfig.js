import { initializeApp } from 'firebase/app';
import { Platform } from 'react-native';

// Optionally import the services that you want to use
// import {...} from "firebase/auth";
// import {...} from "firebase/database";
// import {...} from "firebase/firestore";
// import {...} from "firebase/functions";
// import {...} from "firebase/storage";
import { getAnalytics } from 'firebase/analytics';

// Initialize Firebase
const firebaseConfig = {
    apiKey: 'AIzaSyCFxD5lZjM_mzGTra-7agy-3E367gmZbr8',
    authDomain: 'reported1-app.firebaseapp.com',
    databaseURL: 'https://reported1-app.firebaseio.com',
    projectId: 'reported1-app',
    // storageBucket: 'reported1-app.appspot.com',
    // messagingSenderId: 'sender-id',
    appId: Platform.OS === 'android' ? 'com.reported1.android' : 'com.reported1.ios',
    // measurementId: 'G-measurement-id',
};

const Firebase = initializeApp(firebaseConfig);
// For more information on how to access Firebase in your project,
// see the Firebase documentation: https://firebase.google.com/docs/web/setup#access-firebase
const analytics = getAnalytics();

export default analytics;
