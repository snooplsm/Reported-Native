import React from "react";
import {
  AsyncStorage,
  Alert,
  Text,
  TouchableOpacity,
  View,
  StyleSheet
} from "react-native";
import ComplaintView from "./ComplaintView";
import { categories } from "./Categories.js";
import * as ImagePicker from "expo-image-picker";
import * as Permissions from "expo-permissions";
import * as FileSystem from "expo-file-system";
import * as Constants from "expo-constants";
import TouchSpoof from "./TouchSpoof";
import * as ImageManipulator from "expo-image-manipulator";
import {
  Badge,
  Button,
  Icon,
  Image,
  Input,
  Overlay
} from "react-native-elements";
import { BackHandler, Modal, Picker } from "react-native";
import ImageViewer from "react-native-image-zoom-viewer";
import AddressView from "./AddressView";
import LicenseView from "./LicenseView";
import ImageCarousel from "./ImageCarousel";
import LogoTitle from "./LogoTitle";
import moment from "moment";
import { ScrollView } from "react-navigation";
import { IconStyle, colors } from "./Styles";
import { ProgressBar } from "react-native-paper";
import DateTimePicker from "react-native-modal-datetime-picker";
import { alpr, finds, api, uploadFile, reverseGeocode } from "./Api";

const isEqual = require("react-fast-compare");

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
      showComplaintModal: undefined,
      showAddressModal: undefined,
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
      license: undefined
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
  }

  onClearPressed = () => {
    Alert.alert("Discard Report?", "Discard report and start a new one?", [
      {
        text: "Cancel",
        onPress: () => {}
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
    if (this.state.imageModal) {
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
      console.log("async error", error);
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
      console.log("erorr loading draft", error);
    }
  };

  setState(state, lambda) {
    super.setState(state, () => {
      if (this.modalsShowing) {
        console.log("can go back!!");
        this.props.navigation.setParams({ canGoBack: true });
      } else {
        this.props.navigation.setParams({ canGoBack: false });
      }
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

  get addressModal() {
    if (this.state.showAddressModal) {
      return (
        <View
          style={{
            position: "absolute",
            bottom: 0,
            top: 0,
            left: 0,
            right: 0,
            zIndex: 9999,
            backgroundColor: "white"
          }}
        >
          <AddressView
            location={this.state.location}
            onPress={({ data, place }) => {
              this._address.blur();
              this.setState({
                showAddressModal: false,
                location: { place }
              });
            }}
          />
        </View>
      );
    }
  }

  get complaintModal() {
    if (this.state.showComplaintModal) {
      return (
        <View
          style={{
            position: "absolute",
            bottom: 0,
            top: 0,
            left: 0,
            right: 0,
            zIndex: 9999,
            backgroundColor: "white"
          }}
        >
          <ComplaintView
            onComplaintsChanged={c => {
              this._complaint.blur();
              this.setState({ showComplaintModal: false, complaints: c });
            }}
          />
        </View>
      );
    } else {
      return <></>;
    }
  }

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

  componentWillUnount() {
    this.backHandler.remove();
    clearInterval(this.timeofreportinterval);
  }

  timeofreport(timeofreport) {
    if (!timeofreport) {
      return "";
    }
    const momy = moment(timeofreport);
    if (momy.isValid()) {
      return `${momy.fromNow()} @ ${momy.format("M/D h:mm a")}`;
    }
    return "";
  }

  validatePlate() {
    const okPlate =
      this.state.license &&
      this.state.license.candidate &&
      this.state.license.candidate.plate &&
      this.state.license.candidate.plate.length > 1;
    if (!okPlate) {
      return false;
    }
    const plate = this.state.license.candidate.plate.toUpperCase();
    if (plate.charAt(0) === "T") {
      if (plate.length < 6) {
        return true;
      }
      const tlcRegex = /^T\d{6}C$/g;
      console.log(tlcRegex);
      console.log(plate.match(tlcRegex));
      const match = plate.match(tlcRegex);
      return match && match.length == 1;
    }
    return plate.length > 1;
  }

  alrt(title, message) {
    this.setState({ submitting: false });
    Alert.alert(title, message);
  }

  progressListener(progress) {
    var promise = new Promise(function(resolve, reject) {
      const reducer = (sum, num) => {
        return sum + num.size;
      };
      const plate =
        this.state.license &&
        this.state.license.plate &&
        this.state.license.plate.image;
      console.log("plate is ", plate);
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
        console.log(e);
      });
  }

  submit() {
    if (this.state.complaints.length < 1) {
      this.alrt(
        "Missing Complaint",
        "Select a complaint type: Blocked Bike Lane, Crosswalk, etc."
      );
      return;
    }
    if (!this.state.location) {
      this.alrt("Address Missing", "Location of incident is missing.");
      return;
    }
    if (!this.state.timeofreport) {
      this.alrt(
        "Incident Time Missing",
        "Time you observed infraction is missing."
      );
      return;
    }
    const okPlate = this.validatePlate();
    if (!okPlate) {
      this.alrt("Invalid Plate", "Mke sure the license plate is valid.");
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
          console.log("uploaded license plate");
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
          console.log("error upload", e);
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

    const complaints = this.state.complaints ?? [];
    if (
      this.state.license &&
      this.state.uploadedMedia[this.state.license.url]
    ) {
      license.media = this.state.uploadedMedia[this.state.license.plate.url];
    }
    const place = this.state.location.place;
    const address = place.address_components;

    const geo = this.state.location.place.geometry.location;
    const building = finds(address, "street_number");
    const street = finds(address, "route");
    const city = finds(address, "locality");
    const sublocality = finds(address, "sublocality");
    const premise = finds(address, "premise");
    const county = finds(address, "administrative_area_level_2");
    const state = finds(address, "administrative_area_level_1");
    const zip = finds(address, "postal_code");
    const formatted_address = place.formatted_address;
    this.setState({ submitting: true });
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
        timeofincident: this.state.timeofreport,
        media: this.state.media
          .map(x => this.state.uploadedMedia[x.url])
          .concat(
            [license]
              .filter(x => x && x.plate && x.plate.image)
              .map(x => this.state.uploadedMedia[x.plate.image.url])
          )
      })
      .then(x => {
        this.clear();
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
    if (typeof timeofreport === "string") {
      return moment(timeofreport).toDate();
    }
    return timeofreport;
  }

  clear() {
    this._license.clear();
    this.setState(this.initialState);
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
    if (this.validatePlate()) {
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
    return (
      <>
        {this.state.submitting && (
          <ProgressBar
            style={{
              margin: 0,
              height: 4,
              verticalPadding: 0,
              padding: 0
            }}
            progress={this.state.progress}
            color={colors.orange}
          />
        )}
        <ScrollView>
          <View style={styles.container}>
            {/*<ComplaintView
          onComplaintsChanged={c => this.setState({ complaints: c })}
        />*/}
            <Button
              type="outline"
              buttonStyle={styles.addPhoto}
              containerStyle={styles.addPhotoContainer}
              onPress={this._pickImage}
              title={this.addPhotoText}
            />

            <ImageCarousel
              onItemPressed={({ item, index }) => {
                this.setState({ imageModal: index });
              }}
              entries={this.state.media}
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

            <View>
              <TouchableOpacity
                onPress={() => this.setState({ showAddressModal: true })}
              >
                <Input
                  ref={r => (this._address = r)}
                  caretHidden={true}
                  autoFocus={false}
                  onFocus={x => this.setState({ showAddressModal: true })}
                  label={"Address"}
                  placeholder={"Where you observed infraction"}
                  value={this.addressString}
                />
              </TouchableOpacity>
            </View>

            <LicenseView
              ref={r => (this._license = r)}
              onPlateSelected={plate => {
                const { candidate } = plate;
                console.log("candidate plate", candidate);
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

            {this.state.datePickerVisible && (
              <DateTimePicker
                mode={"datetime"}
                titleIOS={"Time of incident"}
                isVisible={true}
                date={this.time || new Date()}
                onConfirm={date => {
                  this._datePick.blur();
                  this.setState({
                    timeofreport: date,
                    timeofreportstr: this.timeofreport(date),
                    datePickerVisible: undefined
                  });
                }}
                onCancel={() => {
                  this.setState({ datePickerVisible: undefined });
                }}
              />
            )}

            <TouchSpoof
              onPress={() => {
                this.setState({
                  datePickerVisible: true
                });
              }}
            >
              <Input
                ref={r => {
                  this._datePick = r;
                }}
                caretHidden={true}
                autoFocus={false}
                label={"When Incident Occurred"}
                onFocus={x => this.setState({ datePickerVisible: true })}
                placeholder={"Time you observed infraction"}
                value={this.state.timeofreportstr}
              />
            </TouchSpoof>
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

        <Button
          onPress={() => this.submit()}
          title="SUBMIT"
          loading={this.state.submitting}
          titleStyle={{
            fontSize: 22,
            fontWeight: "bold",
            opacity: this.percentageOpacity
          }}
          buttonStyle={[{}, styles.submitButtonStyle]}
        />
        {this.imageModal}
        {this.complaintModal}
        {this.addressModal}
      </>
    );
  }

  _pickImage = async () => {
    const permission = await Permissions.getAsync(Permissions.CAMERA_ROLL);
    // const permission2 = await Permissions.getAsync(
    //   Permissions.WRITE_EXTERNAL_STORAGE
    // );
    const imageLaunch = ImagePicker.launchImageLibraryAsync({
      exif: true,
      mediaTypes: ImagePicker.MediaTypeOptions.All
    });
    const success = result => {
      if (result.cancelled) {
        return;
      }
      const { width, height, uri, type, duration } = result;
      const { exif } = result;
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
          GPSAltitude: altitude,
          GPSLatitude: lat,
          GPSLongitude: lng
        } = exif;

        const lats = lat;
        const lngs = lng;

        //console.log(lats, lngs);

        const timeof =
          timeofreport && moment(timeofreport, "yyyy:MM:DD HH:mm:ss").toDate();

        Object.assign(image, {
          timeofreport: timeof,
          takenAt: timeof,
          altitude: altitude,
          location: { lat: lats, lng: lngs }
        });

        if (!this.state.location) {
          reverseGeocode(image.location)
            .then(places => {
              //console.log(places.results[0]);
              this.setState({ location: { place: places.results[0] } });
            })
            .catch(e => console.log(e));
        }
        if (!this.state.license) {
          let resize = null;
          if (width > height) {
            resize = { width: Math.min(1200, parseInt(width)) };
          } else {
            resize = { height: Math.min(1200, parseInt(height)) };
          }
          console.log("manipulate", resize);
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
            .catch(e => console.log(e));
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
      // this.resizeImages()
      //   .then(x => {
      //     this.setState({ resizedImages: x });
      //   })
      //   .then(r => {})
      //   .catch(x => {
      //     console.error(x);
      //   });
    };
    console.log(permission.status);
    if (permission.status !== "granted") {
      const newPermission = await Permissions.askAsync(Permissions.CAMERA_ROLL);
      if (newPermission.status === "granted") {
        // const newPermission2 = await Permissions.askAsync(
        //   Permissions.PERMISSIONS.WRITE_EXTERNAL_STORAGE
        // );
        // if (newPermission2.status === "granted") {
        imageLaunch.then(success);
        // }
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
    borderRadius: 0,
    padding: 20,
    backgroundColor: colors.orange
  },
  button: {
    width: "30%",
    height: 60
  },
  container: {
    width: "100%",
    height: "100%",
    justifyContent: "space-between",
    marginBottom: 1,
    marginTop: 12
  },
  imageViewer: {
    backgroundColor: "yellow",
    width: 200,
    height: 200
  }
});
