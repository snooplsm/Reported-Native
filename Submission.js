import React from "react";
import { Text, TouchableOpacity, View, StyleSheet } from "react-native";
import ComplaintView from "./ComplaintView";
import { ImagePicker, Permissions } from "expo";
import { Button, Icon, Image, Input } from "react-native-elements";
import { Modal } from "react-native";
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
      headerLeft: <Button title="+1" color="#000" />
    };
  };

  constructor(props) {
    super(props);
    this.state = {
      images: [],
      resizedImages: [],
      datePickerVisible: false,
      imageModal: false,
      uploadedImages: {}
    };
  }

  closeModal() {
    this.setState({ imageModal: false });
  }

  removeImage(index) {
    const { images } = this.state;
    let newImages = [...images];
    newImages.splice(index, 1);
    this.setState({ images: newImages });
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
      this.state.uploadedImages[this.state.license.url]
    ) {
      license.media = this.state.uploadedImages[this.state.license.plate.url];
    }
    const address = this.state.location.place.address_components;
    console.log(address);

    const finds = (address, key) => {
      console.log("find", address, key);
      console.log(address.filter(x => x.types.includes(key)));
      return address
        .filter(x => x.types.includes(key))
        .map(x => x.short_name)
        .shift();
    };

    const building = finds(address, "street_number");
    const geo = this.state.location.place.geometry.location;
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
              onFocus={x => this.setState({ datePickerVisible: true })}
              label={"When Incident Occurred"}
              value={this.state.timeofreportstr}
            />
          </View>
          <View>
            <Input
              label={"Incident Description (optional)"}
              onChange={v => this.setState({ description: v })}
              multiline={true}
              numberOfLines={3}
              textAlignVertical={"top"}
              value={this.state.description}
            />
          </View>
          <Button
            onPress={() => this.submit()}
            title="Submit"
            buttonStyle={styles.submitButtonStyle}
            containerStyle={styles.submitButton}
          />
        </View>
        {this.imageModal}
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
    padding: 10
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
    height: "100%"
  },
  imageViewer: {
    backgroundColor: "yellow",
    width: 200,
    height: 200
  }
});
