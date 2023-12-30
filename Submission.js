import React from "react";
import { Alert, Keyboard, View, StyleSheet, ScrollView } from "react-native";
import { useNavigation } from '@react-navigation/native';
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as ImagePicker from "expo-image-picker";
import * as Constants from "expo-constants";
import TouchSpoof from "./TouchSpoof";
import * as ImageManipulator from "expo-image-manipulator";
import { Button, Icon, Input } from "react-native-elements";
import { BackHandler } from "react-native";
import LicenseView from "./LicenseView";
import ImageCarousel from "./ImageCarousel";
import LogoTitle from "./LogoTitle";
import moment from "moment";
import ordinal from "ordinal";
import { colors, globalStyles } from "./Styles";
import { isSignedIn } from "./Auth";
import setColor from "color";
import FloatingMainButton from "./FloatingMainButton";
import { alpr, api, uploadFile, reverseGeocode } from "./Api";
import ComplaintView from "./ComplaintView";
import { getLocationDataFromExif, checkAddressNotBelongsToNY, findInLocation } from "./utils/locations";
import { checkForNoNullValuesInArray } from "./utils/others";
import ImageViewer from "./components/ImageViewer";
import { KeyboardAwareScrollView } from "react-native-keyboard-aware-scroll-view";
import { SUBMIT_BUTTON_HEIGHT, SUBMIT_BUTTON_PADDING_VERTICAL } from "./common/dimen";
import { useAuth } from "./AuthProvider";

const isEqual = require("react-fast-compare");
const diff = require("deep-diff");

export default function Submission() {
  const navigationOptions = ({ navigation }) => {
    return {
      headerTitle: <LogoTitle />,
      headerLeft: () => {
        if (navigation.getParam("canGoBack")) {
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
      headerRight: (
        <Icon
          onPress={navigation.getParam("onClearPressed")}
          containerStyle={{
            padding: 10,
            opacity: navigation.getParam("isInitialState") === false ? 100 : 0
          }}
          name="cancel"
          color="#000"
        />
      )
    };
  };

  const [stateMedia, setMedia] = React.useState([]);
  const [stateResizedImages, setResizedImages] = React.useState([]);
  const [stateDatePickerVisible, setDatePickerVisible] = React.useState(undefined);
  const [stateTimeofreport, setTimeofreport] = React.useState(undefined);
  const [stateTimeofreportstr, setTimeofreportstr] = React.useState(undefined);
  const [stateComplaints, setComplaints] = React.useState([]);
  const [stateImageModal, setImageModal] = React.useState(undefined);
  const [stateUploadedMedia, setUploadedMedia] = React.useState({});
  const [stateSubmitting, setSubmitting] = React.useState(false);
  const [stateDescription, setDescription] = React.useState('');
  const [stateNotes, setNotes] = React.useState('');
  const [stateLicense, setLicense] = React.useState(undefined);
  const [stateAlpr, setAlpr] = React.useState(undefined);
  const [stateLocation, setLocation] = React.useState(undefined);
  const [stateKeyboard, setKeyboard] = React.useState(undefined);
  const [stateShowAddressModal, setShowAddressModal] = React.useState(false);
  const [stateShowComplaintModal, setShowComplaintModal] = React.useState(false);
  const [statePercent, setPercent] = React.useState(0);

  const _complaint = React.useRef();
  const _license = React.useRef();

  const navigation = useNavigation();
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
      onClearPressed: onClearPressed
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

    () => {
      backHandler.remove();
      // clearInterval(timeofreportinterval);
      keyboardDidShowListener.remove();
      keyboardDidHideListener.remove();
    }
  }, []);

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

  const saveOffline = async () => {
    const myState = {};
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
          if (data) {
            data.submitting = false;
            this.setState(data);
          }
        })
    } catch (error) {
      console.log("erorr loading draft", error);
    }
  };

  /*
  const setState = (state, lambda) => {
    super.setState(state, () => {
      if (this.modalsShowing()) {
        this.props.navigation.setParams({ canGoBack: true });
      } else {
        this.props.navigation.setParams({ canGoBack: false });
      }
      const diffy = diff(this.state, this.initialState);
      const okEqual = isEqual(this.state, this.initialState);
      this.props.navigation.setParams({
        isInitialState: this.modalsShowing || okEqual
      });
      this.saveOffline();
      if (lambda) {
        lambda();
      }
    });
  }
  */

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

  constreportSubmitted = async result => {
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
      stateLicense &&
      stateLicense.candidate &&
      stateLicense.candidate.plate &&
      stateLicense.candidate.plate.length > 1;
    if (!okPlate) {
      return false;
    }
    const plate = stateLicense.candidate.plate.toUpperCase();

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
    if (
      from &&
      plate.match(USPS) &&
      (stateLicense.candidate.state || "").length === 0
    ) {
      const license = stateLicense;
      Alert.alert(
        "Is this a USPS vehicle?",
        "Does this vehicle belong to the US Postal Service?",
        [
          {
            text: "YES",
            onPress: () => {
              const license = stateLicense;
              license.candidate.state = "USPS";
              this.setState({ license }, () => {
                from();
              });
            }
          },
          {
            text: "NO",
            onPress: () => {
              license.candidate.state = "COMMERCIAL";
              this.setState({ license });
              from();
            }
          }
        ]
      );
      return undefined;
    }
    if (
      from &&
      plate.match(NYPD) &&
      (stateLicense.candidate.state || "").length === 0
    ) {
      Alert.alert(
        "Is this an NYPD vehicle?",
        "Does the vehicle belong to the New York City Police Department?",
        [
          {
            text: "YES",
            onPress: () => {
              const license = stateLicense;
              license.candidate.state = "NYPD";
              this.setState({ license }, () => {
                from();
              });
            }
          },
          {
            text: "NO",
            onPress: () => {
              const license = stateLicense;
              license.candidate.state = "UNKNOWN";
              this.setState({ license }, () => {
                from();
              });
            }
          }
        ]
      );
      return undefined;
    }

    return plate.length > 1;
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
        setState({
          progress: loaded / total
        });
      })
      .catch(e => {
        // console.log(e);
      });
  }

  const doSubmit = () => {
    if (stateComplaints.length < 1) {
      alrt(
        "Missing Complaint",
        "Select a complaint type: Blocked Bike Lane, Crosswalk, etc."
      );
      return;
    }
    const okPlate = validatePlate(doSubmit);
    if (okPlate === false) {
      alrt("Invalid License Plate", `${okPlate}`);
      return;
    }
    if (okPlate === undefined) {
      return;
    }
    const place = stateLocation.place;
    const address = place.address_components;
    const plateNeedsUploading = okPlate && stateLicense.plate.image;
    const plateUploaded =
      !plateNeedsUploading ||
      stateUploadedMedia[stateLicense.plate.image.url];
    if (!plateUploaded) {
      setSubmitting(true);
      uploadFile(stateLicense.plate.image, {
        listener: progress => {
          progressListener(progress);
        }
      })
        .then(uploaded => {
          uploaded.type = "S3_IMAGE_LICENSE";
          const uploadedMedia = stateUploadedMedia;
          uploadedMedia[stateLicense.plate.image.url] = uploaded;
          setUploadedMedia(uploadedMedia);
          submit();
        })
        .catch(() => {
          alrt("Error uploading image", "Image upload failed.");
        });
      return;
    }
    const needToUpload = stateMedia.filter(
      x => !stateUploadedMedia[x.url]
    );
    if (needToUpload.length > 0) {
      const file = needToUpload[0];
      setSubmitting(true);
      uploadFile(file, {
        listener: progressListener
      })
        .then(uploaded => {
          if (file.type === "image") {
            uploaded.type = "S3_IMAGE";
          } else {
            uploaded.type = "S3_VIDEO";
          }
          const uploadedMedia = stateUploadedMedia;
          uploadedMedia[file.url || fille.uri] = uploaded;
          setUploadedMedia(uploadedMedia);
          submit();
        })
        .catch(e => {
          alrt("Error uploading media", JSON.stringify(e));
        });
      return;
    }
    setSubmitting(true);

    const license = Object.assign(
      {
        candidate: {
          plate: "TEST"
        },
        plate: {
          region: ""
        }
      },
      stateLicense
    );

    if (
      stateLicense &&
      stateUploadedMedia[stateLicense.url]
    ) {
      license.media = stateUploadedMedia[stateLicense.plate.url];
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
          .map(x => stateUploadedMedia[x.url])
          .concat(
            [license]
              .filter(x => x && x.plate && x.plate.image)
              .map(x => stateUploadedMedia[x.plate.image.url])
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
      });
  }

  const getTime = () => {
    if (!stateTimeofreport) {
      return null;
    }
    return moment(stateTimeofreport).toDate();
  }

  const clear = (lambda) => {
    _license.clear();
    this.setState(this.initialState, () => {
      lambda && lambda();
    });
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

  const _pickImage = async () => {
    // const permission = await Permissions.getAsync(Permissions.CAMERA_ROLL);
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    console.log('perm', permission);
    const result = await ImagePicker.launchImageLibraryAsync({
      exif: true,
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
      console.log('exif', exif);
      const { lat, lng, altitude } = getLocationDataFromExif(exif);
      console.log('coord', lat, lng, altitude);
      // Check if location exists
      if (!lat || !lng) {
        alrt(
          'Missing photo location',
          'This photo does not contain location data.\nPlease select a photo that has valid location data'
        );
        return;
      }
      const timeof =
        timeofreport && moment(timeofreport, "yyyy:MM:DD HH:mm:ss").toDate();

      Object.assign(image, {
        timeofreport: timeof,
        takenAt: timeof,
        altitude: altitude,
        location: { lat, lng }
      });
      const place = await getLocationData({ lat, lng });
      if (!place) return;
      setLocation({ place });
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
        ImageManipulator.manipulateAsync(image.url, [{ resize }])
          .then(r => {
            data.resized = r;
            return alpr.recognize(r);
          })
          .then(result => {
            data.alprResult = result;
            data.images = [data.resized];
            setAlpr(data);
          })
          .catch(e => { });
      }
    }

    const { timeofreport } = image;

    if (timeofreport) {
      var datetime = moment(timeofreport, "yyyy:MM:DD HH:mm:ss").toDate();
      setTimeofreport(datetime);
      setTimeofreportstr(getTimeofreport(datetime));
    }

    setMedia([...stateMedia, image]);

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
                    label={"Address"}
                    placeholder={"Automatically will be extracted from photo"}
                    value={stateLocation?.place?.formatted_address}
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
                  ref={_license}
                  onPlateSelected={plate => {
                    const { candidate } = plate;
                    if (
                      candidate &&
                      candidate.plate &&
                      candidate.plate.length > 0
                    ) {
                      setLicense(plate);
                    } else {
                      setLicense(undefined);
                    }
                  }}
                  license={stateLicense}
                  alpr={stateAlpr}
                />
              </View>
              <View style={styles.inputWrapper}>
                <Input
                  label={"Incident Description (optional)"}
                  onChangeText={v => {
                    setDescription(v);
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
                  onChangeText={v => setNotes(v)}
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
      </KeyboardAwareScrollView>
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
