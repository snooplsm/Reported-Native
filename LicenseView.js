import React from "react";
import {
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  TouchableWithoutFeedback,
  Text,
  View,
  Modal
} from "react-native";
import { Button, Icon, Input, Image } from "react-native-elements";
import * as ImageManipulator from "expo-image-manipulator";
import { ImagePicker, Permissions } from "expo";
import { AutoStyle } from "./Styles";
import { alpr } from "./Api";

export default class LicenseView extends React.Component {
  constructor(props) {
    super(props);
    this.state = this.initialState;
  }

  get licenseFromProps() {
    if (this.state.licensePlate !== "") {
      return this.state.licensePlate;
    }
    const { license } = this.props;
    if (license && license.candidate && license.candidate.plate) {
      return license.candidate.plate;
    }
    return "";
  }

  get initialState() {
    return {
      licenses: [],
      plates: [],
      licensePlate: "",
      showPlatePicker: true,
      alpr: null
    };
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    const old = (prevProps.alpr && prevProps.alpr.images) || [];
    const newz = (this.props.alpr && this.props.alpr.images) || [];
    if (
      newz.length > old.length &&
      this.props.alpr &&
      this.licenseFromProps === ""
    ) {
      this.processImage({
        image: newz[newz.length - 1],
        original: this.props.alpr.original
      });
      return true;
    } else {
      return false;
    }
  }

  processAlpr({ image, original }) {
    const { results } = this.props.alpr.alprResult;
    const images = results.map(result => {
      const { plate, confidence, region, candidates, coordinates } = result;
      plate.region = region;
      const plates = candidates
        .sort((a, b) => b.confidence - a.confidence)
        .filter((thing, index) => {
          return (
            index ===
            candidates.findIndex(obj => {
              return obj.plate === thing.plate;
            })
          );
        });
      const topLeft = coordinates[0];
      const topRight = coordinates[1];
      const bottomLeft = coordinates[2];
      const bottomRight = coordinates[3];

      const originX = parseInt((topLeft.x / image.width) * original.width);
      const originY = parseInt((topLeft.y / image.height) * original.height);
      const width =
        parseInt((topRight.x / image.width) * original.width) - originX;
      const height =
        parseInt((bottomRight.y / image.height) * original.height) - originY;

      const crop = {
        originX,
        originY,
        width,
        height
      };

      return {
        plates: plates,
        imageAsync: ImageManipulator.manipulateAsync(original.url, [
          {
            crop: crop
          }
        ])
      };
    });
    //// console.log('images length', images.length)
    Promise.all(
      images.map(x => {
        return new Promise((resolve, reject) => {
          x.imageAsync
            .then(image => {
              const resp = {
                plates: x.plates,
                image: Object.assign(image, { url: image.uri })
              };
              resolve(resp);
            })
            .catch(f => {
              reject(f);
            });
        });
      })
    )
      .then(res => {
        this.setState({ plates: res });
      })
      .catch(ex => {});
  }

  clear() {
    this.setState(this.initialState);
  }

  processImage({ image, original }) {
    this.processAlpr({ image, original });
  }

  _onPlateSelected(selected) {
    this.setState({
      selected: selected,
      licensePlate: selected.candidate.plate,
      showPlatePicker: false
    });
    if (this.props.onPlateSelected) {
      this.props.onPlateSelected(selected);
    }
  }

  toDegrees(rad) {
    return rad * (180 / Math.PI);
  }

  angleOf(x1, y1, x2, y2) {
    const deltaY = y1 - y2;
    const deltaX = x2 - x1;
    const result = this.toDegrees(Math.atan2(deltaY, deltaX));
    return result;
  }

  toRadians(ang) {
    return ang * (Math.PI / 180.0);
  }

  render() {
    const { license } = this.props;
    return (
      <View>
        {!license &&
          this.state.plates.map((plate, index) => (
            <Image
              containerStyle={{
                paddingLeft: 10,
                paddingRight: 10
              }}
              key={index}
              source={plate.image}
            />
          ))}
        {license &&
          license.plate &&
          license.plate.image &&
          license.plate.image.url && (
            <Image
              containerStyle={{
                paddingLeft: 10,
                paddingRight: 10
              }}
              source={license.plate.image}
            />
          )}
        <Input
          placeholder="ie: T64353"
          autoCapitalize="characters"
          onChangeText={licensePlate => {
            this.setState({ licensePlate: licensePlate.toUpperCase() });
            this._onPlateSelected({
              plate: { region: "" },
              candidate: {
                plate: licensePlate
              },
              media: undefined
            });
          }}
          label={"License Plate"}
          value={this.licenseFromProps}
        />

        <Modal
          visible={this.state.plates.length != 0 && this.state.showPlatePicker}
        >
          <ScrollView>
            <View style={styles.container}>
              <Text style={styles.header}>
                We may have detected the license plate, please choose from the
                following if applicable.
              </Text>
              {this.state.plates.map((plate, index) => {
                return (
                  <View key={index}>
                    <Image style={styles.plateImage} source={plate.image} />
                    <View style={styles.plateContainer}>
                      {plate.plates.map(x => {
                        return (
                          <View key={x.plate} style={styles.plateTextContainer}>
                            <Button
                              type="outline"
                              container={styles.buttonContainer}
                              onPress={() =>
                                this._onPlateSelected({
                                  plate: plate,
                                  candidate: x
                                })
                              }
                              title={`${x.plate} (${parseInt(x.confidence)}%)`}
                            />
                          </View>
                        );
                      })}
                      <Button
                        onPress={() =>
                          this.setState({ showPlatePicker: false })
                        }
                        title="None of these match"
                      />
                    </View>
                  </View>
                );
              })}
            </View>
          </ScrollView>
        </Modal>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  plateImage: {},
  button: {
    width: "30%",
    height: 60
  },
  header: {
    padding: 20
  },
  plateContainer: {
    flexDirection: "column"
  },
  plateTextContainer: {
    flexDirection: "row",
    width: "100%"
  },
  plateText: {
    fontSize: 18
  },
  buttonContainer: {
    paddingTop: 10,
    width: "60%"
  },
  container: {
    flex: 1,
    width: "100%",
    justifyContent: "center",
    alignItems: "center",
    flexDirection: "column"
  },
  imageViewer: {
    backgroundColor: "yellow",
    width: 200,
    height: 200
  },
  modalContainer: {
    position: "absolute",
    top: 0,
    left: 0,
    bottom: 0,
    right: 0
  }
});
