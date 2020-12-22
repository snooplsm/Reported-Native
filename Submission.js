import React from "react";
import {
  AsyncStorage,
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  View,
  StyleSheet,
  Platform
} from "react-native";
import * as ImagePicker from "expo-image-picker";
import * as Permissions from "expo-permissions";
import * as Constants from "expo-constants";
import TouchSpoof from "./TouchSpoof";
import * as ImageManipulator from "expo-image-manipulator";
import {
  Button,
  Icon,
  Input,
} from "react-native-elements";
import { BackHandler } from "react-native";
import ImageViewer from "react-native-image-zoom-viewer";
import LicenseView from "./LicenseView";
import ImageCarousel from "./ImageCarousel";
import LogoTitle from "./LogoTitle";
import moment from "moment";
import { ScrollView } from "react-navigation";
import ordinal from "ordinal";
import { colors } from "./Styles";
import { isSignedIn } from "./Auth";
import setColor from "color";
import {
  alpr,
  finds,
  api,
  uploadFile,
  reverseGeocode,
  pushTokenNeedsRegisteringAsync,
  registerForPushNotificationsAsync
} from "./Api";
import {getLocationDataFromExif} from './Utils/locations'

const isEqual = require("react-fast-compare");
const diff = require("deep-diff");

export default class Submission extends React.Component {
  static navigationOptions = ({ navigation }) => {
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

  get initialState() {
    return {
      media: [],
      resizedImages: [],
      datePickerVisible: undefined,
      timeofreport: undefined,
      timeofreportstr: undefined,
      complaints: [],
      imageModal: undefined,
      uploadedMedia: {},
      submitting: false,
      description: "",
      notes: "",
      license: undefined,
      alpr: undefined,
      location: undefined,
      keyboard: undefined
    };
  }

  constructor(props) {
    super(props);
    this.state = this.initialState;
  }

  componentDidMount() {
    this.backHandler = BackHandler.addEventListener(
      "hardwareBackPress",
      this.onBackPressed
    );
    this.props.navigation.setParams({
      onBackPressed: this.onBackPressed,
      canGoBack: false,
      onClearPressed: this.onClearPressed
    });
    this.loadOffline();
    this.registerToken();
    this.keyboardDidShowListener = Keyboard.addListener(
      "keyboardDidShow",
      this._keyboardDidShow.bind(this)
    );
    this.keyboardDidHideListener = Keyboard.addListener(
      "keyboardDidHide",
      this._keyboardDidHide.bind(this)
    );
    isSignedIn().then(user => {
      if (!user || !user.phone) {
        return;
      }
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
                this.props.navigation.navigate("Profile");
              }
            }
          ]
        );
      }
    });
  }

  _keyboardDidShow() {
    this.setState({ keyboard: true });
  }

  _keyboardDidHide() {
    this.setState({ keyboard: undefined });
  }

  registerToken = async () => {
    const needsRegistering = await pushTokenNeedsRegisteringAsync();
    if (needsRegistering) {
      const result = await registerForPushNotificationsAsync();
    }
  };

  onClearPressed = () => {
    Alert.alert("Discard Report?", "Discard report and start a new one?", [
      {
        text: "Cancel",
        onPress: () => { }
      },
      {
        text: "OK",
        onPress: () => {
          this.clear();
        }
      }
    ]);
  };

  onBackPressed = () => {
    if (!this.state) {
      return false;
    }
    if (this.state.imageModal != undefined) {
      this.setState({ imageModal: undefined });
      return true;
    }
    if (this.state.datePickerVisible) {
      this.setState({ datePickerVisible: undefined });
      return true;
    }
    if (this.state.showAddressModal) {
      this.setState({ showAddressModal: undefined });
      return true;
    }
    if (this.state.showComplaintModal) {
      this.setState({ showComplaintModal: undefined });
      return true;
    }
    return false;
  };

  get modalsShowing() {
    const state = this.state;
    return (
      state.imageModal !== undefined ||
      state.datePickerVisible ||
      state.showAddressModal ||
      state.showComplaintModal
    );
  }

  get draftKey() {
    return `report.draft.${Constants.nativeAppVersion}`;
  }

  saveOffline = async () => {
    try {
      const state = Object.assign(this.state, {});

      await AsyncStorage.setItem(this.draftKey, JSON.stringify(this.state));
    } catch (error) {
      // console.log("async error", error);
    }
  };

  loadOffline = async () => {
    try {
      const draft = await AsyncStorage.getItem(this.draftKey);
      const data = draft && JSON.parse(draft);
      if (data) {
        data.submitting = false;
        this.setState(data);
      }
    } catch (error) {
      // console.log("erorr loading draft", error);
    }
  };

  setState(state, lambda) {
    super.setState(state, () => {
      if (this.modalsShowing) {
        this.props.navigation.setParams({ canGoBack: true });
      } else {
        this.props.navigation.setParams({ canGoBack: false });
      }
      const diffy = diff(this.state, this.initialState);
      //// console.log("diff is", diffy);
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

  closeModal() {
    this.setState({ imageModal: undefined });
  }

  removeImage(index) {
    const { media } = this.state;
    let newImages = [...media];
    newImages.splice(index, 1);
    this.setState({ media: newImages, imageModal: undefined });
  }

  get addressString() {
    const { location } = this.state;
    if (!location) {
      return null;
    }
    const { place } = location;
    if (!place) {
      return null;
    }
    const { address_components: address } = place;
    const premise = finds(address, "premise");
    const building = finds(address, "street_number");
    const street = finds(address, "route");
    const locality = finds(address, "locality");
    const sublocality = finds(address, "neighborhood");
    let toUse = null;
    if (sublocality && sublocality !== locality) {
      toUse = sublocality;
    } else {
      toUse = locality;
    }
    if (premise) {
      return [premise, toUse].filter(x => x).join(" ");
    } else {
      return [[building, street].join(" "), toUse].filter(x => x).join(", ");
    }
  }

  reportSubmitted = async result => {
    const canRegister = pushTokenNeedsRegisteringAsync();
    const needsToEnablePush = canRegister && Platform.OS !== "android";
    const buttons = needsToEnablePush && [
      {
        text: "No",
        onPress: () => { }
      },
      {
        text: "Enable",
        onPress: () => {
          this.registerToken();
        }
      }
    ];
    const thirty =
      result.thirtyDays > 1
        ? `This is your ${ordinal(
          result.thirtyDays
        )} report within a thirty day timespan.`
        : `This is your ${ordinal(result.allTime)} report submitted.`;
    let msg = needsToEnablePush
      ? "Your report has been submitted.  Enable push notifications to get future updates?"
      : `Your report has been submitted.  ${thirty}`;
    Alert.alert("Report Submitted", msg, buttons);
  };

  get imageModal() {
    if (this.state.imageModal !== undefined) {
      return (
        <ImageViewer
          ref={ref => {
            this._imageViewer = ref;
          }}
          style={{
            position: "absolute",
            bottom: 0,
            top: 0,
            left: 0,
            right: 0
          }}
          onClick={() => this.closeModal()}
          footerContainerStyle={{
            bottom: 0,
            left: 0,
            right: 0,
            position: "absolute",
            zIndex: 9999
          }}
          renderFooter={index => {
            return (
              <View
                style={{
                  flex: 1,
                  flexDirection: "row",
                  flexWrap: "nowrap",
                  justifyContent: "flex-end"
                }}
              >
                <Icon
                  iconStyle={{
                    padding: 14
                  }}
                  size={26}
                  color="white"
                  type="material"
                  name="remove-circle-outline"
                  onPress={() => this.removeImage(index)}
                />
              </View>
            );
          }}
          imageUrls={this.state.media}
        />
      );
    } else {
      return <></>;
    }
  }

  componentWillMount() {
    this.timeofreportinterval = setInterval(() => {
      const time = this.timeofreport(this.state.timeofreport);
      if (this.state.timeofreportstr !== time) {
        this.setState({ timeofreportstr: time });
      }
    }, 60000);
  }

  componentWillUnmount() {
    this.backHandler.remove();
    clearInterval(this.timeofreportinterval);
    this.keyboardDidShowListener.remove();
    this.keyboardDidHideListener.remove();
  }

  timeofreport(timeofreport) {
    if (!timeofreport) {
      return undefined;
    }
    const momy = moment(timeofreport);
    if (momy.isValid()) {
      return `${momy.fromNow()} @ ${momy.format("M/D h:mm a")}`;
    }
    return "";
  }

  validatePlate(from) {
    const okPlate =
      this.state.license &&
      this.state.license.candidate &&
      this.state.license.candidate.plate &&
      this.state.license.candidate.plate.length > 1;
    if (!okPlate) {
      return false;
    }
    const plate = this.state.license.candidate.plate.toUpperCase();

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
      (this.state.license.candidate.state || "").length === 0
    ) {
      const license = this.state.license;
      Alert.alert(
        "Is this a USPS vehicle?",
        "Does this vehicle belong to the US Postal Service?",
        [
          {
            text: "YES",
            onPress: () => {
              const license = this.state.license;
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
      (this.state.license.candidate.state || "").length === 0
    ) {
      Alert.alert(
        "Is this an NYPD vehicle?",
        "Does the vehicle belong to the New York City Police Department?",
        [
          {
            text: "YES",
            onPress: () => {
              const license = this.state.license;
              license.candidate.state = "NYPD";
              this.setState({ license }, () => {
                from();
              });
            }
          },
          {
            text: "NO",
            onPress: () => {
              const license = this.state.license;
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

  alrt(title, message) {
    this.setState({ submitting: false });
    Alert.alert(title, message);
  }

  progressListener(progress) {
    var promise = new Promise(function (resolve, reject) {
      const reducer = (sum, num) => {
        return sum + num.size;
      };
      const plate =
        this.state.license &&
        this.state.license.plate &&
        this.state.license.plate.image;
      // console.log("plate is ", plate);
      const total =
        this.state.media.reduce(reducer, 0) + ((plate && plate.size) || 0);
      const loaded =
        this.state.uploadedMedia.reduce(reducer, 0) + progress.loaded;
      resolve({ total, loaded });
    });
    promise
      .then(prog => {
        setState({
          progress: loaded / total
        });
      })
      .catch(e => {
        // console.log(e);
      });
  }

  submit() {
    const { location, complaints, timeofreport } = this.state;
    if (complaints.length < 1) {
      this.alrt(
        "Missing Complaint",
        "Select a complaint type: Blocked Bike Lane, Crosswalk, etc."
      );
      return;
    }
    if (!location) {
      this.alrt(
        'Missing Complaint',
        `Can't extract location data from photo. Please, select proper photo.`
      );
      return;
    }

    if (!timeofreport) {
      this.alrt('Missing Complaint', `Can't extract time. Please, select proper photo.`);
      return;
    }

    const okPlate = this.validatePlate(this.submit);
    if (okPlate === false) {
      this.alrt("Invalid License Plate", `${okPlate}`);
      return;
    }
    if (okPlate === undefined) {
      return;
    }
    if (!timeofreport) {
      this.alrt(
        "Incident Time Missing",
        "Time you observed infraction is missing."
      );
      return;
    }
    const plateNeedsUploading = okPlate && this.state.license.plate.image;
    const plateUploaded =
      !plateNeedsUploading ||
      this.state.uploadedMedia[this.state.license.plate.image.url];
    if (!plateUploaded) {
      this.setState({ submitting: true });
      uploadFile(this.state.license.plate.image, {
        listener: progress => {
          this.progressListener(progress);
        }
      })
        .then(uploaded => {
          // console.log("uploaded license plate");
          uploaded.type = "S3_IMAGE_LICENSE";
          const uploadedMedia = this.state.uploadedMedia;
          uploadedMedia[this.state.license.plate.image.url] = uploaded;
          this.setState({ uploadedMedia });
          this.submit();
        })
        .catch(e => {
          this.alrt("Error uploading image", "Image upload failed.");
        });
      return;
    }
    const needToUpload = this.state.media.filter(
      x => !this.state.uploadedMedia[x.url]
    );
    if (needToUpload.length > 0) {
      const file = needToUpload[0];
      this.setState({ submitting: true });
      uploadFile(file, {
        listener: this.progressListener
      })
        .then(uploaded => {
          if (file.type == "image") {
            uploaded.type = "S3_IMAGE";
          } else {
            uploaded.type = "S3_VIDEO";
          }
          const uploadedMedia = this.state.uploadedMedia;
          uploadedMedia[file.url || fille.uri] = uploaded;
          this.setState({ uploadedMedia });
          this.submit();
        })
        .catch(e => {
          this.alrt("Error uploading media", "Media upload failed.");
          // console.log("error upload", e);
        });
      return;
    }
    const place = location.place;
    const address = place.address_components;
    const city = finds(address, "locality");
    if (city.toUpperCase() !== 'NEW YORK') {
      this.alrt(
        'Error geo coordinates',
        'Photo geo coordinates not in NYC, please, load proper photo.'
      );
      this.setState({
        timeofreport: undefined,
        location: undefined,
        timeofreportstr: undefined,
      });
      return;
    }
    this.setState({ submitting: true });

    const license = Object.assign(
      {
        candidate: {
          plate: "TEST"
        },
        plate: {
          region: ""
        }
      },
      this.state.license
    );

    if (
      this.state.license &&
      this.state.uploadedMedia[this.state.license.url]
    ) {
      license.media = this.state.uploadedMedia[this.state.license.plate.url];
    }

    const geo = place.geometry.location;
    const building = finds(address, "street_number");
    const street = finds(address, "route");
    const sublocality = finds(address, "sublocality");
    const premise = finds(address, "premise");
    const county = finds(address, "administrative_area_level_2");
    const state = finds(address, "administrative_area_level_1");
    const zip = finds(address, "postal_code");
    const formatted_address = place.formatted_address;
    api
      .report({
        description: this.state.description,
        notes: this.state.notes,
        complaintIds: complaints.map(x => x.id),
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
        timeofincident: timeofreport,
        media: this.state.media
          .map(x => this.state.uploadedMedia[x.url])
          .concat(
            [license]
              .filter(x => x && x.plate && x.plate.image)
              .map(x => this.state.uploadedMedia[x.plate.image.url])
          )
      })
      .then(x => {
        this.clear(() => {
          this.reportSubmitted(x.data);
        });
      })
      .catch(e => {
        const request = e.request;
        const response = request && request.response;
        const error = response && JSON.parse(response);
        const code = error && error.code;
        if (code) {
          switch (code) {
            case 216:
              this.alrt(
                "Duplicate Report",
                "Appears this report has already been submitted."
              );
              break;
            default:
              this.alrt(
                "Problem submitting report",
                "An error occured while submitting your report.  Please try again."
              );
          }
        } else
          this.alrt(
            "Problem submitting report",
            "An error occured while submitting your report.  Please try again."
          );
      });
  }

  get time() {
    const { timeofreport } = this.state;
    if (!timeofreport) {
      return null;
    }
    return moment(timeofreport).toDate();
  }

  clear(lambda) {
    this._license.clear();
    this.setState(this.initialState, () => {
      lambda && lambda();
    });
  }

  get addPhotoText() {
    if (this.state.media && this.state.media.length > 0) {
      return "Add Another Photo/Video";
    } else {
      return "Add Photo/Video";
    }
  }

  get percentageOpacity() {
    let percent = 0.3;
    if (this.validatePlate() === true) {
      percent += 0.2;
    }
    if (this.state.complaints.length === 1) {
      percent += 0.1;
    }
    if (this.state.location) {
      percent += 0.1;
    }
    if (this.state.media.length > 0) {
      percent += 0.1;
    }
    if (this.state.timeofreport) {
      percent += 0.2;
    }
    return Math.min(1, percent);
  }

  render() {
    const { media, timeofreportstr } = this.state;
    return (
      <>
        <KeyboardAvoidingView behavior="padding" style={styles.container}>
          <ScrollView>
            <View style={styles.container}>
              <Button
                type="outline"
                buttonStyle={styles.addPhoto}
                containerStyle={styles.addPhotoContainer}
                onPress={this._pickImage}
                title={this.addPhotoText}
              />

              {/* <Button     <-- For the future debug button
                type="outline"
                buttonStyle={styles.addPhoto}
                containerStyle={styles.addPhotoContainer}
                onPress={() => console.log('MEDIA DATA', media)}
                title={'CONSOLE MEDIA'}
              /> */}
              <ImageCarousel
                onItemPressed={({ item, index }) => {
                  this.setState({ imageModal: index });
                }}
                entries={media}
              />
              <View>
                <TouchSpoof
                  onPress={() => this.setState({ showComplaintModal: true })}
                >
                  <Input
                    ref={r => (this._complaint = r)}
                    caretHidden={true}
                    autoFocus={false}
                    onFocus={x => this.setState({ showComplaintModal: true })}
                    label={"Complaint"}
                    placeholder={"Complaint Type, Blocked Bike lane, Crosswalk"}
                    value={this.state.complaints.map(x => x.name).join(", ")}
                  />
                </TouchSpoof>
              </View>
              <Input
                editable={false}
                label={"Address"}
                placeholder={"Automatically will be extracted from photo"}
                value={this.addressString}
              />
              <Input
                editable={false}
                label={"When Incident Occurred"}
                placeholder={"Automatically will be extracted from photo"}
                value={timeofreportstr}
              />
              <LicenseView
                ref={r => (this._license = r)}
                onPlateSelected={plate => {
                  const { candidate } = plate;
                  if (
                    candidate &&
                    candidate.plate &&
                    candidate.plate.length > 0
                  ) {
                    this.setState({ license: plate });
                  } else {
                    this.setState({ license: undefined });
                  }
                }}
                license={this.state.license}
                alpr={this.state.alpr}
              />
              <Input
                label={"Incident Description (optional)"}
                onChangeText={v => {
                  this.setState({ description: v });
                }}
                multiline={true}
                numberOfLines={3}
                placeholder={"Add any additional details to provide to 311"}
                textAlignVertical={"top"}
                value={this.state.description}
              />
              <Input
                label={"Notes (optional and private)"}
                onChangeText={v => this.setState({ notes: v })}
                multiline={true}
                numberOfLines={3}
                placeholder={
                  "Notes that only you will see and will not be sent to 311"
                }
                textAlignVertical={"top"}
                value={this.state.notes}
              />
              <View style={{ height: 100 }} />
            </View>
          </ScrollView>
          <View
            style={{
              width: "100%",
              height: 4,
              opacity: this.state.submitting ? 100 : 0,
              backgroundColor: setColor(colors.orange)
                .alpha(0.38)
                .rgb()
                .string()
            }}
          >
            <View
              style={{
                width: `${this.state.percent ?? 0}%`,
                height: "100%",
                backgroundColor: colors.orange
              }}
            />
          </View>
          <Button
            onPress={() => this.submit()}
            title="SUBMIT"
            loading={this.state.submitting}
            titleStyle={{
              fontSize: 24,
              fontWeight: "bold",
              opacity: this.percentageOpacity
            }}
            containerStyle={{
              marginBottom: this.state.keyboard ? 64 : 0
            }}
            buttonStyle={[
              { padding: 20, borderRadius: 0 },
              styles.submitButtonStyle
            ]}
          />
        </KeyboardAvoidingView>
        {this.imageModal}
      </>
    );
  }

  _pickImage = async () => {
    const { location, license } = this.state;
    const permission = await Permissions.getAsync(Permissions.CAMERA_ROLL);
    const imageLaunch = ImagePicker.launchImageLibraryAsync({
      exif: true,
      mediaTypes: ImagePicker.MediaTypeOptions.All
    });
    const success = result => {
      if (result.cancelled) {
        return;
      }
      const { width, height, uri, type, duration, exif } = result;
      const image = {
        url: uri,
        width: width,
        height: height,
        duration: duration,
        type: type
      };

      if (exif) {
        const {
          DateTimeOriginal: timeofreport,
        } = exif;

        const {
          lat, lng, altitude
        } = getLocationDataFromExif(exif)

        const timeof =
          timeofreport && moment(timeofreport, "yyyy:MM:DD HH:mm:ss").toDate();

        Object.assign(image, {
          timeofreport: timeof,
          takenAt: timeof,
          altitude: altitude,
          location: { lat, lng }
        });

        if (!location) {
          reverseGeocode(image.location)
            .then(places => {
              const place = places.results[0];
              this.setState({ location: { place } });
            })
            .catch(e => { });
        }
        if (!license) {
          let resize = null;
          if (width > height) {
            resize = { width: Math.min(1200, parseInt(width)) };
          } else {
            resize = { height: Math.min(1200, parseInt(height)) };
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
              this.setState({
                alpr: data
              });
            })
            .catch(e => { });
        }
      }

      const { timeofreport } = image;

      if (timeofreport) {
        var datetime = moment(timeofreport, "yyyy:MM:DD HH:mm:ss").toDate();
        this.setState({
          timeofreport: datetime,
          timeofreportstr: this.timeofreport(datetime)
        });
      }

      const media = [...this.state.media, image];
      this.setState({ media: media });
    };
    if (permission.status !== "granted") {
      const newPermission = await Permissions.askAsync(Permissions.CAMERA_ROLL);
      if (newPermission.status === "granted") {
        imageLaunch.then(success);
      }
    } else {
      imageLaunch.then(success);
    }
  };
}

const styles = StyleSheet.create({
  addPhoto: {
    // height: 100
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
    height: 60
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
  }
});
