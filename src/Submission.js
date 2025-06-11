import React from "react";
import { Alert, Keyboard, View, StyleSheet, ScrollView, Platform, Pressable } from "react-native";
import { useNavigation } from '@react-navigation/native';
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as ImagePicker from "expo-image-picker";
import * as Constants from "expo-constants";
import TouchSpoof from "./TouchSpoof";
import * as ImageManipulator from "expo-image-manipulator";
import { Button, Icon, Input } from "react-native-elements";
import { BackHandler } from "react-native";
import { KeyboardAwareScrollView } from "react-native-keyboard-aware-scroll-view";
import moment from "moment";
import ordinal from "ordinal";
import setColor from "color";
import * as Location from 'expo-location';

// import LicenseView from "./LicenseView";
import { LicenseView } from "./LicenseView2";
import ImageCarousel from "./ImageCarousel";
import LogoTitle from "./LogoTitle";
import { colors, globalStyles } from "./Styles";
import FloatingMainButton from "./FloatingMainButton";
import { alpr, api, uploadFile, reverseGeocode } from "./Api";
import ComplaintView from "./ComplaintView";
import { getLocationDataFromExif, checkAddressNotBelongsToNY, findInLocation } from "./utils/locations";
import { checkForNoNullValuesInArray } from "./utils/others";
import ImageViewer from "./components/ImageViewer";
import { SUBMIT_BUTTON_HEIGHT, SUBMIT_BUTTON_PADDING_VERTICAL } from "./common/dimen";
import { useAuth } from "./AuthProvider";

const isEqual = require("react-fast-compare");
const diff = require("deep-diff");

export default function Submission({ navigation }) {
  const [stateMedia, setMedia] = React.useState([]);
  const [stateResizedImages, setResizedImages] = React.useState([]);
  const [stateDatePickerVisible, setDatePickerVisible] = React.useState(undefined);
  const [stateTimeofreport, setTimeofreport] = React.useState(undefined);
  const [stateTimeofreportstr, setTimeofreportstr] = React.useState(undefined);
  const [stateTimeofreportExif, setTimeofreportExif] = React.useState(undefined);
  const [stateComplaints, setComplaints] = React.useState([]);
  const [stateImageModal, setImageModal] = React.useState(undefined);
  const [stateUploadedMedia, setUploadedMedia] = React.useState({});
  const [stateSubmitting, setSubmitting] = React.useState(false);
  const [stateDescription, setDescription] = React.useState('');
  const [stateNotes, setNotes] = React.useState('');
  const [stateLicense, setLicense] = React.useState(undefined);
  const [stateAlpr, setAlpr] = React.useState(undefined);
  const [stateAlprImage, setAlprImage] = React.useState(undefined);
  const [stateLocation, setLocation] = React.useState(undefined);
  const [stateKeyboard, setKeyboard] = React.useState(undefined);
  const [stateShowAddressModal, setShowAddressModal] = React.useState(false);
  const [stateShowComplaintModal, setShowComplaintModal] = React.useState(false);
  const [statePercent, setPercent] = React.useState(0);
  const [stateData, setStateData] = React.useState({});
  const [addressLabel, setAddressLabel] = React.useState('Address');

  const _complaint = React.useRef();

  // const navigation = useNavigation();
  // console.log('subm navi', navigation);
  const auth = useAuth();

  const draftKey = `report.draft.${Constants.nativeAppVersion}`;

  React.useEffect(() => {
    const backHandler = BackHandler.addEventListener(
      "hardwareBackPress",
      onBackPressed
    );
    navigation.setParams({
      onBackPressed: onBackPressed,
      canGoBack: false,
      onClearPressed: onClearPressed,
    });
    // navigation.setOptions(navigationOptions(navigation));
    navigation.setOptions({
      headerTitle: <LogoTitle />,
      headerLeft: () => {
        // if (navigation.getParam("canGoBack")) {
        if (false) {
          return (
            <Icon
              onPress={navigation.getParam("onBackPressed")}
              isVisible={navigation.getParam("canGoBack") === true}
              containerStyle={{ padding: 10 }}
              name="arrow-back"
              color="#000"
            />
          );
        } else {
          return <></>;
        }
      },
      headerRight: () => (
        <Icon
          // onPress={navigation.getParam("onClearPressed")}
          onPress={() => onClearPressed()}
          containerStyle={{
            padding: 10,
            // opacity: navigation.getParam("isInitialState") === false ? 100 : 0
          }}
          name="cancel"
          color="#000"
        />
      ),
    });

    loadOffline();
    const keyboardDidShowListener = Keyboard.addListener(
      "keyboardDidShow",
      _keyboardDidShow
    );
    const keyboardDidHideListener = Keyboard.addListener(
      "keyboardDidHide",
      _keyboardDidHide
    );

    const user = auth.userObj;
    console.log('user loaded', user);
    if (!!user && !!user.phone) {
      const phoneMatches = user.phone.match(
        /^(\+\d{1,2}\s)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}$/
      );
      if (!phoneMatches) {
        Alert.alert(
          "Invalid Phone Number",
          `We have detected that you have an invalid phone number of ${user.phone}.`,
          [
            {
              text: "Fix",
              onPress: () => {
                navigation.navigate("Profile");
              }
            }
          ]
        );
      }
    }

    () => {
      backHandler.remove();
      // clearInterval(timeofreportinterval);
      keyboardDidShowListener.remove();
      keyboardDidHideListener.remove();
    }
  }, [auth.userObj]);

  const _keyboardDidShow = () => {
    setKeyboard(true);
  }

  const _keyboardDidHide = () => {
    setKeyboard(undefined);
  }

  const onClearPressed = () => {
    Alert.alert("Discard Report?", "Discard report and start a new one?", [
      {
        text: "Cancel",
        onPress: () => { }
      },
      {
        text: "OK",
        onPress: () => {
          clear();
        }
      }
    ]);
  };

  const onBackPressed = () => {
    if (stateImageModal !== undefined) {
      setImageModal(undefined);
      return true;
    }
    if (stateDatePickerVisible) {
      setDatePickerVisible(undefined);
      return true;
    }
    if (stateShowAddressModal) {
      setShowAddressModal(undefined);
      return true;
    }
    if (stateShowComplaintModal) {
      setShowComplaintModal(undefined);
      return true;
    }
    return false;
  };

  const modalsShowing = () => {
    const state = this.state;
    return (
      state.imageModal !== undefined ||
      state.datePickerVisible ||
      state.showAddressModal ||
      state.showComplaintModal
    );
  }

  const saveOffline = async (data) => {
    let myState = data === null ? {} : {
      media: stateMedia,
      timeofreportExif: stateTimeofreportExif,
      complaints: stateComplaints,
      description: stateDescription,
      notes: stateNotes,
      plate: stateLicense,
      location: stateLocation,
    };
    if (!!data) {
      myState = { ...myState, ...data };
    }

    console.log('save offline', myState)
    try {
      await AsyncStorage.setItem(draftKey, JSON.stringify(myState));
    } catch (error) {
      console.log("async error", error);
    }
  };

  const loadOffline = () => {
    try {
      AsyncStorage.getItem(draftKey)
        .then((draft) => {
          const data = draft && JSON.parse(draft);
          console.log('load offline', data);
          if (data) {
            data.submitting = false;
            setMedia(data.media || []);
            setTimeOfReportField(data.timeofreportExif);
            setComplaints(data.complaints || []);
            setDescription(data.description);
            setNotes(data.notes);
            setLicense(data.plate);
            setLocation(data.location);
          }
        })
    } catch (error) {
      console.log("erorr loading draft", error);
    }
  };

  const setState = async (state, lambda) => {
    if (modalsShowing()) {
      navigation.setParams({ canGoBack: true });
    } else {
      navigation.setParams({ canGoBack: false });
    }
    // const diffy = diff(this.state, this.initialState);
    /*
    const okEqual = isEqual(this.state, this.initialState);
    this.props.navigation.setParams({
      isInitialState: this.modalsShowing || okEqual
    });
    */
    await saveOffline();
    if (lambda) {
      lambda();
    }
  }

  const closeModal = () => {
    setImageModal(undefined);
  };

  const removeImage = index => {
    let newImages = [...stateMedia];
    newImages.splice(index, 1);
    setMedia(newImages);
    setImageModal(undefined);
  };

  const addressString = () => {
    try {
      return stateLocation.place.formatted_address;
    } catch (e) {
      return null;
    }
  }

  const reportSubmitted = async result => {
    const thirty =
      result.thirtyDays > 1
        ? `This is your ${ordinal(
          result.thirtyDays
        )} report within a thirty day timespan.`
        : `This is your ${ordinal(result.allTime)} report submitted.`;
    let msg = `Your report has been submitted.  ${thirty}`;
    Alert.alert("Report Submitted", msg);
  };

  const ComplaintModal = () => {
    if (stateShowComplaintModal) {
      return (
        <View style={styles.complaintModal}>
          <ComplaintView
            onComplaintsChanged={c => {
              _complaint.current.blur();
              setShowComplaintModal(false);
              setComplaints(c);
              saveOffline({ complaints: c });
            }}
          />
        </View>
      );
    } else {
      return <></>;
    }
  }

  const ImageModal = () => {
    if (stateImageModal !== undefined) {
      return (
        <ImageViewer
          imagesURLs={stateMedia}
          onCloseModal={closeModal}
          onRemoveImage={removeImage}
        />
      );
    } else {
      return null;
    }
  }

  const getTimeofreport = (timeofreport) => {
    if (!timeofreport) {
      return undefined;
    }
    const momy = moment(timeofreport);
    if (momy.isValid()) {
      return `${momy.fromNow()} @ ${momy.format("M/D h:mm a")}`;
    }
    return "";
  }

  const validatePlate = (from) => {
    const okPlate =
      !!stateLicense &&
      !!stateLicense.plate &&
      !!stateLicense.region
    if (!okPlate) {
      return false;
    }
    const plate = stateLicense.plate;
    const licState = stateLicense.region;

    const regexes = [/^[TNWY]\d{6}$/g, /^[TNWY]\d{6}[^C]{*}/g, /^\d{6}[C]/g];
    if (
      regexes.filter(x => {
        const matches = plate.match(x);
        return matches && matches.length === 1;
      }).length > 0
    ) {
      return "TLC plates start with T and end with C.  Please fix.";
    }
    const NYPD = /^[\d]{4}$/g;
    const USPS = /^[\d]{7}$/g;
    const license = stateLicense;
    if (
      from &&
      plate.match(USPS) &&
      (licState || "").length === 0
    ) {
      Alert.alert(
        "Is this a USPS vehicle?",
        "Does this vehicle belong to the US Postal Service?",
        [
          {
            text: "YES",
            onPress: () => {
              license.region = "USPS";
              setLicense(license);
              saveOffline({ plate: plate, region: license.region })
                .then(() => from());
            }
          },
          {
            text: "NO",
            onPress: () => {
              license.region = "COMMERCIAL";
              setLicense(license);
              saveOffline({ plate: plate, region: license.region })
                .then(() => from());
            }
          }
        ]
      );
      return undefined;
    }
    if (
      from &&
      plate.match(NYPD) &&
      (licState || "").length === 0
    ) {
      Alert.alert(
        "Is this an NYPD vehicle?",
        "Does the vehicle belong to the New York City Police Department?",
        [
          {
            text: "YES",
            onPress: () => {
              license.region = "NYPD";
              setLicense(license);
              saveOffline({ plate: plate, region: license.region })
                .then(() => from());
            }
          },
          {
            text: "NO",
            onPress: () => {
              license.region = "UNKNOWN";
              setLicense(license);
              saveOffline({ plate: plate, region: license.region })
                .then(() => from());
            }
          }
        ]
      );
      return undefined;
    }

    return true;
  }

  const alrt = (title, message) => {
    setSubmitting(false);
    Alert.alert(title, message);
  }

  const progressListener = (progress) => {
    const promise = new Promise(function (resolve) {
      const reducer = (sum, num) => {
        return sum + num.size;
      };
      const plate =
        stateLicense &&
        stateLicense.plate &&
        stateLicense.plate.image;
      const total =
        stateMedia.reduce(reducer, 0) + ((plate && plate.size) || 0);
      const loaded =
        stateUploadedMedia.reduce(reducer, 0) + progress.loaded;
      resolve({ total, loaded });
    });
    promise
      .then(() => {
        setPercent(loaded / total);
      })
      .catch(e => {
        // console.log(e);
      });
  }

  const uploadAllMedia = async () => {
    const uploadedMedia = { ...stateUploadedMedia };

    //for (const media of stateMedia) {
    //};
    await Promise.all(stateMedia.map(async (media) => {
      // console.warn("upload start", media);
      const uploaded = await uploadFile(auth.userObj, media, { listener: progressListener });
      if (media.type === "image") {
        uploaded.type = "S3_IMAGE";
      } else {
        uploaded.type = "S3_VIDEO";
      }
      // console.warn("uploaded", uploaded);
      uploadedMedia[media.url || media.uri] = uploaded;
    }));

    return uploadedMedia;
  }

  const uploadAllMediaOld = () => {
    const uploadedMedia = { ...stateUploadedMedia };

    while (true) {
      const plateNeedsUploading = !!stateLicense.plate.image;
      const plateUploaded =
        !plateNeedsUploading ||
        uploadedMedia[stateLicense.plate.image.url];
      if (!plateUploaded) {
        uploadFile(auth.userObj, stateLicense.plate.image, {
          listener: progress => {
            progressListener(progress);
          }
        })
          .then(uploaded => {
            uploaded.type = "S3_IMAGE_LICENSE";
            uploadedMedia[stateLicense.plate.image.url] = uploaded;
          })
          .catch(() => {
            alrt("Error uploading image", "Image upload failed.");
          });
        break;
      }

      const needToUpload = stateMedia.filter(
        x => !uploadedMedia[x.url]
      );
      if (needToUpload.length > 0) {
        const file = needToUpload[0];
        uploadFile(auth.userObj, file, {
          listener: progressListener
        })
          .then(uploaded => {
            if (file.type === "image") {
              uploaded.type = "S3_IMAGE";
            } else {
              uploaded.type = "S3_VIDEO";
            }
            // console.warn("uploaded", uploaded);
            uploadedMedia[file.url || file.uri] = uploaded;
          })
          .catch(e => {
            alrt("Error uploading media", JSON.stringify(e));
          });
        break;
      }
    }

    return uploadedMedia;
  }

  const doSubmit = async () => {
    if (stateComplaints.length < 1) {
      alrt(
        "Missing Complaint",
        "Select a complaint type: Blocked Bike Lane, Crosswalk, etc."
      );
      return;
    }

    const place = stateLocation?.place;
    if (!place) {
      alrt(
        'Missing address',
        'Select the address of the complaint',
      );
      return;
    }

    const okPlate = validatePlate(doSubmit);
    if (okPlate === false) {
      alrt("Invalid License Plate", "");
      return;
    }
    if (okPlate === undefined) {
      return;
    }

    const address = place.address_components;
    setSubmitting(true);
    const uploadedMedia = await uploadAllMedia();
    setUploadedMedia(uploadedMedia);

    const license = {
      candidate: {
        plate: stateLicense.plate,
      },
      plate: {
        region: stateLicense.region,
      }
    };

    if (
      stateLicense &&
      uploadedMedia[stateLicense.url]
    ) {
      license.media = uploadedMedia[stateLicense.plate.url];
    }

    const geo = place.geometry.location;
    const building = findInLocation(address, "street_number");
    const street = findInLocation(address, "route");
    const sublocality = findInLocation(address, "sublocality");
    const premise = findInLocation(address, "premise");
    const city = findInLocation(address, "locality");
    const county = findInLocation(address, "administrative_area_level_2");
    const state = findInLocation(address, "administrative_area_level_1");
    const zip = findInLocation(address, "postal_code");
    const formatted_address = place.formatted_address;
    const areAddressFieldsNonNull = checkForNoNullValuesInArray([
      building,
      street
    ]);
    if (!areAddressFieldsNonNull) {
      alrt(
        "Invalid location",
        "The location is not a valid street address."
      );
      return;
    }

    api
      .report({
        description: stateDescription,
        notes: stateNotes,
        complaintIds: stateComplaints.map(x => x.id),
        license: {
          plate: license.candidate.plate,
          state: license.plate.region
        },
        address: Object.assign({
          formatted_address,
          premise,
          building,
          street,
          city,
          county,
          state,
          zip,
          sublocality,
          location: geo
        }),
        timeofincident: stateTimeofreport,
        media: stateMedia
          .map(x => uploadedMedia[x.url])
          .concat(
            [license]
              .filter(x => x && x.plate && x.plate.image)
              .map(x => uploadedMedia[x.plate.image.url])
          )
      })
      .then(x => {
        clear(() => {
          reportSubmitted(x.data);
        });
      })
      .catch(e => {
        console.log('report err', e.response && e.response.data);
        const response = e.response;
        const code = response && response.code;
        if (code) {
          switch (code) {
            case 216:
              alrt(
                "Duplicate Report",
                "Appears this report has already been submitted."
              );
              break;
            default:
              alrt(
                "Problem submitting report",
                "An error occured while submitting your report.  Please try again."
              );
          }
        } else
          alrt(
            "Problem submitting report",
            "An error occured while submitting your report.  Please try again."
          );
        /*
        Alert.alert(
          JSON.stringify(Object.assign({}, e).response.status),
          Object.assign({}, e).response.data
        );
        */
        console.warn('err submitting report', Object.assign({}, e));
      })
      .finally(() => {
        setSubmitting(false);
      });
  }

  const getTime = () => {
    if (!stateTimeofreport) {
      return null;
    }
    return moment(stateTimeofreport).toDate();
  }

  const clear = (lambda) => {
    setMedia([]);
    setTimeofreport(undefined);
    setTimeofreportstr(undefined);
    setTimeofreportExif(undefined);
    setComplaints([]);
    setDescription('');
    setNotes('');
    setLicense(undefined);
    setAlpr(undefined);
    setLocation(undefined);
    setPercent(0);
    saveOffline(null)
      .then(() => {
        lambda && lambda();
      });
  }

  const getCurrentAddress = async () => {
    let { status } = await Location.requestForegroundPermissionsAsync();
    console.log('loc status', status);
    if (status !== 'granted') {
      alrt('Access denied', 'Permission to access location was denied');
      return;
    }

    console.log('loc started');
    let location = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Highest,
      maximumAge: 10000,
    });
    console.log('loc', location.coords);

    return {
      lat: location.coords.latitude,
      lng: location.coords.longitude,
      altitude: location.coords.altitude,
    }
  }

  const getAddPhotoText = () => {
    if (stateMedia && stateMedia.length > 0) {
      return "Add Another Photo";
    } else {
      return "Add Photo";
    }
  }

  const getLocationData = async (imageLocation) => {
    return await reverseGeocode(imageLocation)
      .then(places => {
        if (!places || !places.results || !places.results[0]) {
          alrt('Error getting coordinates');
        } else {
          const place = places.results[0];
          if (!checkAddressNotBelongsToNY(place)) {
            return place;
          } else throw new Error('The location is outside of NYC. Reported only works in NYC.');
        }
      })
      .catch(e => {
        alrt('Error geo coordinates', e.message);
      });
  }

  const setTimeOfReportField = (timeofreport) => {
    var datetime = moment(timeofreport, "yyyy:MM:DD HH:mm:ss").toDate();
    console.log('set time', timeofreport, datetime);
    setTimeofreportExif(timeofreport);
    setTimeofreport(datetime);
    setTimeofreportstr(getTimeofreport(datetime));
  }

  const _pickImage = async () => {
    const updateState = {};
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    console.log('perm', permission);
    const result = await ImagePicker.launchImageLibraryAsync({
      exif: true,
      legacy: Platform.OS === 'android', // needed to get GPS coordinates
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
    });

    console.log('image', JSON.stringify(result, null, ' '));
    if (result.canceled) {
      return;
    }

    const { assets } = result;
    if (!assets || !assets[0]) {
      return;
    }

    const imageInfo = assets[0];
    const exif = assets[0].exif;

    const image = {
      url: imageInfo.uri,
      width: imageInfo.width,
      height: imageInfo.height,
      duration: imageInfo.duration,
      type: imageInfo.type,
    };
    // Check if exif exists
    console.log('exif', exif);
    if (!exif) {
      alrt(
        'Missing exif data',
        `Can't extract exif data from photo.\nPlease, select proper photo.`
      );
      return;
    } else {
      const { DateTimeOriginal: timeofreport } = exif;
      // Check time image was picked
      if (!timeofreport) {
        alrt('Photo creation date is missing', `Can't extract time.\nPlease, select proper photo.`);
        return;
      }
      let { lat, lng, altitude } = getLocationDataFromExif(exif);
      // console.warn('coord', lat, lng, altitude);
      // Check if location exists
      if (!lat || !lng) {
        /*
        alrt(
          'Missing photo location',
          'Please select a photo that has valid location data or select location manually'
        );
        */
        // return;
        setAddressLabel('Address (loading...)');
        const currentLocation = await getCurrentAddress();
        console.log('current loc', currentLocation);
        if (!!currentLocation) {
          setAddressLabel('Address (current)');
          lat = currentLocation.lat;
          lng = currentLocation.lng;
          altitude = currentLocation.altitude;
        } else {
          setAddressLabel('Address');
        }
      } else {
        setAddressLabel('Address (from photo)');
      }

      const timeof =
        timeofreport && moment(timeofreport, "yyyy:MM:DD HH:mm:ss").toDate();

      if (timeofreport) {
        setTimeOfReportField(timeofreport);
        updateState.timeofreportExif = timeofreport;
      }

      Object.assign(image, {
        timeofreport: timeof,
        takenAt: timeof,
        altitude: altitude,
        location: { lat, lng }
      });
      // console.warn('image', image);
      if (!!lat && !!lng) {
        const place = await getLocationData({ lat, lng });
        if (!!place) {
          setLocation({ place });
          updateState.location = { place };
        }
      }
      console.log('location', updateState.location);
      if (!stateLicense) {
        let resize = null;
        if (image.width > image.height) {
          resize = { width: Math.min(1200, parseInt(image.width)) };
        } else {
          resize = { height: Math.min(1200, parseInt(image.height)) };
        }
        const data = {
          original: image
        };
        const imageResult = await ImageManipulator.manipulateAsync(image.url, [{ resize }]);
        // console.warn('imageResult', imageResult);
        data.resized = imageResult;
        const imageAlrp = await alpr.recognize(imageResult);
        console.warn('alpr', imageAlrp);
        data.images = [data.resized];
        updateState.alpr = imageAlrp;
        setAlpr(imageAlrp);
        setAlprImage(imageResult);
        /*
          .then(r => {
            data.resized = r;
            return alpr.recognize(r);
          })
          .then(result => {
            data.alprResult = result;
            data.images = [data.resized];
            setAlpr(data);
            updateState.alpr = data;
          })
          .catch(e => { });
          */
      }
    }

    updateState.media = [...stateMedia, image];
    setMedia(updateState.media);

    await saveOffline(updateState);

    /*
    if (permission.status !== "granted") {
      const newPermission = await Permissions.askAsync(Permissions.CAMERA_ROLL);
      if (newPermission.status === "granted") {
        imageLaunch.then(success);
      }
    } else {
      imageLaunch.then(success);
    }
    */
  };

  return (
    <>
      <KeyboardAwareScrollView
        style={globalStyles.mainContainer}
        viewIsInsideTabBar
        extraScrollHeight={SUBMIT_BUTTON_HEIGHT + SUBMIT_BUTTON_PADDING_VERTICAL * 2}>
        <View style={styles.container}>
          <View style={styles.inputWrapper}>
            <Button
              type="outline"
              buttonStyle={styles.addPhoto}
              containerStyle={styles.addPhotoContainer}
              onPress={_pickImage}
              title={getAddPhotoText()}
            />
          </View>
          <ImageCarousel
            onItemPressed={({ item, index }) => {
              setImageModal(index);
            }}
            entries={stateMedia}
          />
          {!!stateMedia && !!stateMedia.length ?
            <>
              <View style={styles.inputWrapper}>
                <TouchSpoof
                  onPress={() => setShowComplaintModal(true)}
                >
                  <Input
                    ref={_complaint}
                    caretHidden={true}
                    autoFocus={false}
                    onFocus={x => setShowComplaintModal(true)}
                    label={"Complaint"}
                    placeholder={"Complaint Type, Blocked Bike lane, Crosswalk"}
                    value={stateComplaints.map(x => x.name).join(", ")}
                  />
                </TouchSpoof>
              </View>
              <View style={styles.inputWrapper}>
                <ScrollView
                  horizontal={true}
                  showsHorizontalScrollIndicator={false}
                >
                  <Input
                    editable={false}
                    label={addressLabel}
                    placeholder={"Automatically will be extracted from photo"}
                    value={stateLocation?.place?.formatted_address}
                    onKeyPress={() => alrt('clicked', 'address')}
                  />
                </ScrollView>
              </View>
              <View style={styles.inputWrapper}>
                <Input
                  editable={false}
                  label={"When Incident Occurred"}
                  placeholder={"Automatically will be extracted from photo"}
                  value={stateTimeofreportstr}
                />
              </View>
              <View style={styles.inputWrapper}>
                <LicenseView
                  onPlateSelected={plate => {
                    if (
                      plate.plate &&
                      plate.region
                    ) {
                      setLicense(plate);
                      saveOffline({ plate });
                    } else {
                      setLicense(undefined);
                      saveOffline({ plate: undefined });
                    }
                  }}
                  alpr={stateAlpr}
                  alprImage={stateAlprImage}
                  licensePlate={stateLicense}
                />
              </View>
              <View style={styles.inputWrapper}>
                <Input
                  label={"Incident Description (optional)"}
                  onChangeText={v => {
                    setDescription(v);
                    saveOffline({ description: v });
                  }}
                  multiline={true}
                  numberOfLines={3}
                  placeholder={"Add any additional details to provide to 311"}
                  textAlignVertical={"top"}
                  value={stateDescription}
                />
              </View>
              <View style={styles.inputWrapper}>
                <Input
                  label={"Notes (optional and private)"}
                  onChangeText={v => {
                    setNotes(v);
                    saveOffline({ notes: v });
                  }}
                  multiline={true}
                  numberOfLines={3}
                  placeholder={
                    "Notes that only you will see and will not be sent to 311"
                  }
                  textAlignVertical={"top"}
                  value={stateNotes}
                />
              </View>
            </>
            :
            <></>
          }
        </View>
      </KeyboardAwareScrollView >
      <View
        style={{
          width: "100%",
          height: 4,
          opacity: stateSubmitting ? 100 : 0,
          backgroundColor: setColor(colors.orange)
            .alpha(0.38)
            .rgb()
            .string()
        }}
      >
        <View
          style={{
            width: `${statePercent ?? 0}%`,
            height: "100%",
            backgroundColor: colors.orange
          }}
        />
      </View>
      <FloatingMainButton
        isEnabled={!!stateMedia && !!stateMedia.length}
        isLoading={stateSubmitting}
        onPress={() => doSubmit()}
        title={"SUBMIT"}
        containerStyle={styles.submitButtonWrapper}
      />
      <ImageModal />
      <ComplaintModal />
    </>
  );

}

const styles = StyleSheet.create({
  addPhoto: {
    height: 55
  },
  addPhotoContainer: {
    padding: 10
  },
  submitButtonStyle: {
    backgroundColor: colors.orange
  },
  button: {
    width: "30%",
    height: SUBMIT_BUTTON_HEIGHT
  },
  container: {
    width: "100%",
    height: "100%",
    justifyContent: "space-between"
  },
  imageViewer: {
    backgroundColor: "yellow",
    width: 200,
    height: 200
  },
  submitButtonWrapper: {
    paddingHorizontal: 10,
    paddingVertical: SUBMIT_BUTTON_PADDING_VERTICAL
  },
  inputWrapper: {
    marginLeft: -8,
    marginRight: -8
  },
  complaintModal: {
    position: "absolute",
    bottom: 0,
    top: 0,
    left: 0,
    right: 0,
    zIndex: 9999,
    backgroundColor: "white"
  }
});
