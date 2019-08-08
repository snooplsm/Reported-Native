import React from "react";
import {
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
import * as ImageManipulator from "expo-image-manipulator";
import {
  Badge,
  Button,
  Icon,
  Image,
  Input,
  Overlay
} from "react-native-elements";
import { Modal, Picker } from "react-native";
import ImageViewer from "react-native-image-zoom-viewer";
import AddressView from "./AddressView";
import LicenseView from "./LicenseView";
import ImageCarousel from "./ImageCarousel";
import moment from "moment";
import { IconStyle } from "./Styles";
import DateTimePicker from "react-native-modal-datetime-picker";
import { alpr, api, uploadFile, reverseGeocode } from "./Api";

export default class Submission extends React.Component {
  static navigationOptions = ({ navigation }) => {
    return {
      headerTitle: "Report",
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
          return null;
        }
      }
    };
  };

  get initialState() {
    return {
      media: [],
      resizedImages: [],
      datePickerVisible: false,
      complaints: [],
      imageModal: false,
      uploadedMedia: {},
      submitting: false,
      description: null,
      notes: null,
      license: null
    };
  }

  constructor(props) {
    super(props);
    this.state = this.initialState;
  }

  componentDidMount() {
    this.props.navigation.setParams({
      onBackPressed: this.onBackPressed,
      canGoBack: false
    });
  }

  onBackPressed = () => {
    console.log("on back pressed");
    if (!this.state) {
      console.log("null state");
      return;
    }
    if (this.state.imageModal !== false) {
      this.setState({ imageModal: false });
    }
    if (this.state.datePickerVisible) {
      this.setState({ datePickerVisible: false });
    }
    if (this.state.showAddressModal) {
      this.setState({ showAddressModal: false });
    }
    if (this.state.showComplaintModal) {
      this.setState({ showComplaintModal: false });
    }
    this.props.navigation.setParams({ canGoBack: false });
  };

  setState(state, lambda) {
    super.setState(state, () => {
      if (
        this.state.imageModal ||
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
    const building = finds(address, "street_number");
    const street = finds(address, "route");
    return [building, street].join(" ");
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

  submit() {
    console.log(this.state.media);
    const plateUploaded =
      !this.state.license ||
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
          this.setState({ submitting: false });
          console.log("plate upload error");
        });
      return;
    }
    console.log("mediaaaaa", this.state.uploadedMedia);
    if (
      !this.state.location ||
      !this.state.timeofreport ||
      this.state.complaints.length < 1 ||
      !this.state.timeofreport
    ) {
      this.setState({ submitting: false });
      return;
    }
    const needToUpload = this.state.media.filter(
      x => !this.state.uploadedMedia[x.url]
    );
    if (needToUpload.length > 0) {
      const file = needToUpload[0];
      console.log("uploadss");
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
          this.setState({ submitting: false });
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
          region: "NY"
        }
      },
      this.state.license
    );
    if (!license) {
      return;
    }

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
        alert("success");
        this.setState(this.initialState, () => {
          this.forceUpdate();
        });
      })
      .catch(e => {
        console.log(e);
        this.setState({ submitting: false });
      });
  }

  addPhotoText() {
    if (this.state.media && this.state.media.length > 0) {
      return "Add Another Photo/Video";
    } else {
      return "Add Photo/Video";
    }
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
              <TouchableOpacity
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
              </TouchableOpacity>
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
          buttonStyle={styles.submitButtonStyle}
          containerStyle={styles.submitButton}
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
              data.images = [image];
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
    padding: 20
  },
  submitButton: {
    position: "absolute",
    bottom: 0,
    right: 0,
    width: "100%"
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
