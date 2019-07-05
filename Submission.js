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
import { ImagePicker, Permissions } from "expo";
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
import { ImageManipulator } from "expo";
import AddressView from "./AddressView";
import LicenseView from "./LicenseView";
import ImageCarousel from "./ImageCarousel";
import moment from "moment";
import { IconStyle } from "./Styles";
import DateTimePicker from "react-native-modal-datetime-picker";
import { alpr, api, uploadFile } from "./Api";

export default class Submission extends React.Component {
  static navigationOptions = ({ navigation }) => {
    return {
      headerTitle: "Report",
      headerLeft: (
        <Icon
          onPress={navigation.getParam("onBackPressed")}
          containerStyle={{ padding: 10 }}
          name="arrow-back"
          color="#000"
        />
      )
    };
  };

  constructor(props) {
    super(props);
    this.state = {
      images: [],
      resizedImages: [],
      datePickerVisible: false,
      complaints: [],
      imageModal: false,
      uploadedImages: {}
    };
  }

  componentDidMount() {
    this.props.navigation.setParams({ onBackPressed: this.onBackPressed });
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
  };

  closeModal() {
    this.setState({ imageModal: false });
  }

  removeImage(index) {
    const { images } = this.state;
    let newImages = [...images];
    newImages.splice(index, 1);
    this.setState({ images: newImages });
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
              console.log(place);
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
    console.log("imageModal", this.state.imageModal);
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
          imageUrls={this.state.images}
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

  componentWillUnMount() {
    clearInterval(this.timeofreportinterval);
  }

  timeofreport(timeofreport) {
    console.log(timeofreport);
    const momy = moment(timeofreport);
    console.log(momy);
    if (momy.isValid()) {
      return `${momy.fromNow()} @ ${momy.format("M/d h:mm a")}`;
    }
    return "";
  }

  finds(address, key) {
    console.log("find", address, key);
    console.log(address.filter(x => x.types.includes(key)));
    return address
      .filter(x => x.types.includes(key))
      .map(x => x.short_name)
      .shift();
  }

  submit() {
    const plateUploaded =
      !this.state.license ||
      this.state.uploadedImages[this.state.license.plate.image.url];
    if (!plateUploaded) {
      uploadFile(this.state.license.plate.image)
        .then(uploaded => {
          this.state.uploadedImages[
            this.state.license.plate.image.url
          ] = uploaded;
          this.submit();
        })
        .catch(e => {
          console.log("plate upload error");
        });
      return;
    }
    const needToUpload = this.state.images.filter(
      x => !this.state.uploadedImages[x.url]
    );
    if (needToUpload.length > 0) {
      const file = needToUpload[0];
      console.log("uploadss");
      uploadFile(file)
        .then(uploaded => {
          this.state.uploadedImages[file.url] = uploaded;
          this.submit();
        })
        .catch(e => {
          console.log("error upload", e);
        });
      return;
    }

    const license = Object.assign(
      {
        candidate: {
          plate: "TEST"
        },
        region: "NY"
      },
      this.state.license
    );
    if (!license) {
      return;
    }

    const complaints = this.state.complaints ?? [];
    if (
      this.state.license &&
      this.state.uploadedImages[this.state.license.url]
    ) {
      license.media = this.state.uploadedImages[this.state.license.plate.url];
    }
    const address = this.state.location.place.address_components;
    console.log(address);

    const finds = this.finds;

    const geo = this.state.location.place.geometry.location;
    const building = finds(address, "street_number");
    const street = finds(address, "route");
    const city = finds(address, "locality");
    const sublocality = finds(address, "sublocality");
    const county = finds(address, "administrative_area_level_2");
    const state = finds(address, "administrative_area_level_1");
    const zip = finds(address, "postal_code");
    console.log(this.state.location.data);
    api.report({
      description: "foofuckshit",
      complaintIds: complaints.map(x => x.id),
      license: {
        plate: license.candidate.plate,
        state: license.plate.region
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
      media: this.state.images
        .filter(x => !this.state.uploadedImages[x.url])
        .map(x => this.state.uploadedImages[x.url])
    });
  }

  addPhotoText() {
    if (this.state.images && this.state.images.length > 0) {
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
              entries={this.state.images}
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
              images={this.state.images}
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
              <Input
                ref={r => {
                  this._datePick = r;
                }}
                caretHidden={true}
                autoFocus={false}
                onFocus={x => this.setState({ datePickerVisible: true })}
                label={"When Incident Occurred"}
                placeholder={"Time you observed infraction"}
                value={this.state.timeofreportstr}
              />
            </View>
            <View>
              <Input
                label={"Incident Description (optional)"}
                onChange={v => this.setState({ description: v })}
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
                onChange={v => this.setState({ notes: v })}
                multiline={true}
                numberOfLines={3}
                placeholder={
                  "Notes that only you will see and will not be sent to 311"
                }
                textAlignVertical={"top"}
                value={this.state.description}
              />
            </View>
            <View style={{ height: 100 }} />
          </View>
        </ScrollView>
        <Button
          onPress={() => this.submit()}
          title="Submit"
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
    const resizeAsync = this.state.images.map(x => {
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
    const imageLaunch = ImagePicker.launchImageLibraryAsync({
      exif: true,
      mediaTypes: ImagePicker.MediaTypeOptions.All
    });
    const success = result => {
      if (result.cancelled) {
        return;
      }
      const { exif } = result;
      const { width, height, uri, type } = result;
      const {
        DateTimeOriginal: timeofreport,
        GPSAltitude: altitude,
        GPSLatitude: lat,
        GPSLongitude: lng
      } = exif;

      if (timeofreport) {
        console.log("we have a time of report");
        var datetime = moment(timeofreport, "yyyy:MM:dd HH:mm:ss").toDate();
        console.log(datetime);
        this.setState({
          timeofreport: datetime,
          timeofreportstr: this.timeofreport(datetime)
        });
      }
      const image = {
        url: uri,
        width: width,
        height: height,
        exif: exif,
        lat: lat,
        lng: lng,
        timeofimage: timeofreport
      };
      const images = [...this.state.images, image];
      this.setState({ images: images });
      this.resizeImages()
        .then(x => {
          this.setState({ resizedImages: x });
        })
        .catch(x => {
          console.error(x);
        });
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
