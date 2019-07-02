import React from "react";
import {
  StyleSheet,
  TouchableOpacity,
  TouchableWithoutFeedback,
  Text,
  View,
  Modal
} from "react-native";
import { Button, Icon, Input, Image } from "react-native-elements";
import { ImageManipulator, ImagePicker, Permissions } from "expo";
import { AutoStyle } from "./Styles";
import { alpr } from "./Api";
import { result } from "./alpr";

export default class LicenseViewModal extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      licenses: [],
      result: result,
      plates: [],
      licensePlate: "",
      showPlatePicker: true
    };
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    const old = prevProps.images || [];
    const newz = this.props.images || [];
    if (old.length !== newz.length) {
      this.processImage(newz[newz.length - 1]);
      return true;
    } else {
      return false;
    }
  }

  processAlpr(image) {
    const { width, height, results } = this.state.result;
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
      const originX = parseInt((topLeft.x / image.width) * image.width);
      const originY = parseInt((topLeft.y / image.height) * image.height);
      const width =
        parseInt((topRight.x / image.width) * image.width) - originX;
      const height =
        parseInt((bottomRight.y / image.height) * image.height) - originY;

      const rotate = this.angleOf(topLeft.x, topLeft.y, topRight.x, topRight.y);
      console.log(rotate);
      const centerX = image.width / 2;
      const centerY = image.height / 2;
      const r2 = rotate;
      const rad = this.toRadians(r2);
      const newX =
        Math.cos(rad) * (originX - centerX) -
        Math.sin(rad) * (originY - centerY) +
        centerX;
      const newY =
        Math.sin(rad) * (originX - centerX) +
        Math.cos(rad) * (originY - centerY) +
        centerY;
      const imgW = image.width;
      const imgH = image.height;

      const transposeW = parseInt((newX / image.width) * image.width);
      const transposeH = parseInt((newY / image.height) * image.height);

      console.log({
        rad,
        rotate,
        originX,
        originY,
        newX,
        newY,
        imgW,
        imgH,
        transposeW,
        transposeH
      });
      const crop = {
        originX: originX,
        originY: originY,
        width: width,
        height: height
      };
      console.log(crop);
      //console.log("cropping",crop)
      //console.log("image ", image.width,image.height)
      return {
        plates: plates,
        imageAsync: ImageManipulator.manipulateAsync(image.url, [
          {
            rotate: rotate
          },
          {
            crop: crop
          }
        ])
      };
    });
    //console.log('images length', images.length)
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

  processImage(image) {
    this.processAlpr(image);
  }

  _onPlateSelected(selected) {
    //console.log(selected)
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
    return (
      <View>
        {this.state.plates.map((plate, index) => (
          <Image
            containerStyle={{
              paddingLeft: 10,
              paddingRight: 10
            }}
            key={index}
            source={plate.image}
          />
        ))}
        <Input
          placeholder="[T64353]"
          autoCapitalize="characters"
          onChangeText={licensePlate =>
            this.setState({ licensePlate: licensePlate.toUpperCase() })
          }
          label={"License Plate"}
          value={this.state.licensePlate}
        />

        <Modal
          visible={this.state.plates.length != 0 && this.state.showPlatePicker}
        >
          <View style={styles.container}>
            {this.state.plates.map((plate, index) => {
              return (
                <View key={index}>
                  <Image style={styles.plateImage} source={plate.image} />
                  <Text style={styles.header}>
                    We may have detected the license plate, please choose from
                    the following if applicable.
                  </Text>
                  <View style={styles.plateContainer}>
                    {plate.plates.map(x => {
                      return (
                        <View key={x.plate} style={styles.plateTextContainer}>
                          <Text style={styles.plateText}>
                            {x.plate} ({x.confidence.toFixed(1)})
                          </Text>
                          <Button
                            onPress={() =>
                              this._onPlateSelected({
                                plate: plate,
                                candidate: x
                              })
                            }
                            icon={
                              <Icon
                                name="check"
                                size={12}
                                type="material"
                                color="white"
                              />
                            }
                          />
                        </View>
                      );
                    })}
                    <Button
                      onPress={() => this.setState({ showPlatePicker: false })}
                      title="None of these match"
                    />
                  </View>
                </View>
              );
            })}
          </View>
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
    flexDirection: "row"
  },
  plateText: {
    fontSize: 18
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
