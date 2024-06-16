import { initializeApp } from 'firebase/app';

// Optionally import the services that you want to use
// import {...} from "firebase/auth";
// import {...} from "firebase/database";
// import {...} from "firebase/firestore";
// import {...} from "firebase/functions";
// import {...} from "firebase/storage";
import { analytics } from 'firebase/analytics';

// Initialize Firebase
const firebaseConfig = {
    apiKey: 'AIzaSyCFxD5lZjM_mzGTra-7agy-3E367gmZbr8',
    authDomain: 'reported1-app.firebaseapp.com',
    databaseURL: 'https://reported1-app.firebaseio.com',
    projectId: 'reported1-app',
    storageBucket: 'reported1-app.appspot.com',
    messagingSenderId: 'sender-id',
    appId: 'com.reported1.android',
    measurementId: 'G-measurement-id',
};

const app = initializeApp(firebaseConfig);
// For more information on how to access Firebase in your project,
// see the Firebase documentation: https://firebase.google.com/docs/web/setup#access-firebase
