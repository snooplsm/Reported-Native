import React from "react";
import {
  Alert,
  Text,
  TouchableOpacity,
  View,
  StyleSheet,
  ScrollView
} from "react-native";
import ComplaintView from "./ComplaintView";
import { categories } from "./Categories.js";
import * as ImagePicker from "expo-image-picker";
import * as Permissions from "expo-permissions";
import * as FileSystem from "expo-file-system";
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
import { IconStyle, colors } from "./Styles";
import DateTimePicker from "react-native-modal-datetime-picker";
import { alpr, api, uploadFile, reverseGeocode } from "./Api";

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
          containerStyle={{ padding: 10, opacity: 0 }}
          name="arrow-back"
          color="#000"
        />
      )
    };
  };

  get initialState() {
    return {
      media: [],
      resizedImages: [],
      datePickerVisible: false,
      timeofreport: undefined,
      complaints: [],
      imageModal: false,
      uploadedMedia: {},
      submitting: false,
      description: null,
      notes: null,
      license: null,
      alpr: undefined,
      location: undefined
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
      canGoBack: false
    });
  }

  onBackPressed = () => {
    console.log("on back pressed");
    if (!this.state) {
      console.log("null state");
      return false;
    }
    if (this.state.imageModal !== false) {
      this.setState({ imageModal: false });
      return true;
    }
    if (this.state.datePickerVisible) {
      this.setState({ datePickerVisible: false });
      return true;
    }
    if (this.state.showAddressModal) {
      this.setState({ showAddressModal: false });
      return true;
    }
    if (this.state.showComplaintModal) {
      this.setState({ showComplaintModal: false });
      return true;
    }
  };

  setState(state, lambda) {
    super.setState(state, () => {
      if (
        this.state.imageModal === true ||
        this.state.datePickerVisible ||
        this.state.showAddressModal ||
        this.state.showComplaintModal
      ) {
        this.props.navigation.setParams({ canGoBack: true });
      } else {
        this.props.navigation.setParams({ canGoBack: false });
      }
      if (lambda) {
        lambda();
      }
    });
  }

  closeModal() {
    this.setState({ imageModal: false });
  }

  removeImage(index) {
    const { media } = this.state;
    let newImages = [...media];
    newImages.splice(index, 1);
    this.setState({ media: newImages });
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
    const finds = this.finds;
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
            onPress={({ data, place }) => {
              console.log(place);
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
      console.log("show");
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
              console.log(c);
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
    if (this.state.imageModal !== false) {
      console.log("showing modal");
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
                  backgroundColor: "red",
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
      this.setState({ timeofreportstr: time });
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

  finds(address, key) {
    return address
      .filter(x => x.types.includes(key))
      .map(x => x.short_name)
      .shift();
  }

  validatePlate() {
    console.log(this.state.license);
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
      if (plate.length > 6 && plate.charAt(plate.length - 1) !== "C") {
        return false;
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
      uploadFile(this.state.license.plate.image)
        .then(uploaded => {
          uploaded.type = "S3_IMAGE_LICENSE";
          this.state.uploadedMedia[
            this.state.license.plate.image.url
          ] = uploaded;
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
      uploadFile(file)
        .then(uploaded => {
          if (file.type == "image") {
            uploaded.type = "S3_IMAGE";
          } else {
            uploaded.type = "S3_VIDEO";
          }
          this.state.uploadedMedia[file.url] = uploaded;
          this.submit();
        })
        .catch(e => {
          this.alrt("Error uploading media", "Media upload failed.");
          console.log("error upload", e);
        });
      return;
    }

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
    const address = this.state.location.place.address_components;

    const finds = this.finds;

    const geo = this.state.location.place.geometry.location;
    const building = finds(address, "street_number");
    const street = finds(address, "route");
    const city = finds(address, "locality");
    const sublocality = finds(address, "sublocality");
    const premise = finds(address, "premise");
    const county = finds(address, "administrative_area_level_2");
    const state = finds(address, "administrative_area_level_1");
    const zip = finds(address, "postal_code");
    this.setState({ submitting: true });
    api
      .report({
        description: this.state.description,
        notes: this.state.notes,
        complaintIds: complaints.map(x => x.id),
        license: {
          plate: license.candidate.plate,
          state: license.plate.region,
          media: license.media
        },
        address: Object.assign({
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
        media: this.state.media.map(x => this.state.uploadedMedia[x.url])
      })
      .then(x => {
        this._license.clear();
        this.setState(this.initialState, () => {});
      })
      .catch(e => {
        this.alrt(
          "Problem submitting report",
          "An error occured while submitting your report.  Please try again."
        );
      });
  }

  addPhotoText() {
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
              title={this.addPhotoText()}
            />
            <ImageCarousel
              onItemPressed={({ item, index }) => {
                console.log("index", index);
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
              onPlateSelected={plate => this.setState({ license: plate })}
              alpr={this.state.alpr}
            />

            <DateTimePicker
              mode={"datetime"}
              titleIOS={"Time of incident"}
              isVisible={this.state.datePickerVisible}
              date={this.state.timeofreport}
              onConfirm={date => {
                this._datePick.blur();
                this.setState({
                  timeofreport: date,
                  timeofreportstr: this.timeofreport(date),
                  datePickerVisible: false
                });
              }}
              onCancel={() => {
                this.setState({ datePickerVisible: false });
              }}
            />
            <View>
              <TouchableOpacity
                onPress={() => {
                  console.log("datepickers");
                  this.setState({
                    datePickerVisible: true
                  });
                }}
              >
                <Input
                  ref={r => {
                    this._datePick = r;
                  }}
                  pointerEvents="none"
                  caretHidden={true}
                  autoFocus={false}
                  onFocus={x => this.setState({ datePickerVisible: true })}
                  label={"When Incident Occurred"}
                  placeholder={"Time you observed infraction"}
                  value={this.state.timeofreportstr}
                />
              </TouchableOpacity>
            </View>
            <View>
              <Input
                label={"Incident Description (optional)"}
                onChangeText={v => {
                  console.log(v);
                  this.setState({ description: v });
                }}
                multiline={true}
                numberOfLines={3}
                placeholder={"Add any additional details to provide to 311"}
                textAlignVertical={"top"}
                value={this.state.description}
              />
            </View>
            <View>
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
            </View>
            <View style={{ height: 100 }} />
          </View>
        </ScrollView>
        <Button
          onPress={() => this.submit()}
          title="Submit"
          loading={this.state.submitting}
          buttonStyle={[
            {
              opacity: this.percentageOpacity
            },
            styles.submitButtonStyle
          ]}
        />
        {this.imageModal}
        {this.complaintModal}
        {this.addressModal}
      </>
    );
  }

  resizeImages = async () => {
    const resizeAsync = this.state.media.map(x => {
      let crop = null;
      if (x.width < x.height) {
        crop = {
          originX: (x.height - x.width) / 2,
          originY: 0,
          width: x.width,
          height: x.width
        };
      } else {
        crop = {
          originY: (x.width - x.height) / 2,
          originX: 0,
          width: x.height,
          height: x.height
        };
      }
      console.log("crop", crop);
      return ImageManipulator.manipulateAsync(x.url, [
        {
          crop: crop
        },
        {
          resize: {
            width: 200,
            height: 200
          }
        }
      ]);
    });
    return Promise.all(resizeAsync);
  };

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
          timeofreport && moment(timeofreport, "yyyy:MM:dd HH:mm:ss").toDate();

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
            .then(result => result.json())
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
        var datetime = moment(timeofreport, "yyyy:MM:dd HH:mm:ss").toDate();
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
  addPhoto: {},
  addPhotoContainer: {
    paddingLeft: 10,
    paddingRight: 10,
    paddingBottom: 10,
    paddingTop: 10
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
    marginBottom: 10,
    marginTop: 10
  },
  imageViewer: {
    backgroundColor: "yellow",
    width: 200,
    height: 200
  }
});
