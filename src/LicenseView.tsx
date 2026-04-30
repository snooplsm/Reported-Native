import React from "react";
import {
  ScrollView,
  StyleSheet,
  Text,
  View,
  Modal
} from "react-native";
import { Button, Icon, Input, Image } from "react-native-elements";
import * as ImageManipulator from "expo-image-manipulator";

type LicenseViewProps = {
  license?: any;
  alpr?: any;
  onPlateSelected?: (selected: any) => void;
};

type LicenseViewState = {
  licenses: any[];
  plates: any[];
  licensePlate: string;
  licenseState?: string;
  showPlatePicker: boolean;
  alpr: any;
  selected?: any;
};

export default class LicenseView extends React.Component<LicenseViewProps, LicenseViewState> {
  constructor(props: LicenseViewProps) {
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

  get licenseStateFromProps() {
    if (this.state.licenseState !== "") {
      return this.state.licenseState;
    }
    const { license } = this.props;
    if (license && license.plate && license.plate.region) {
      return license.plate.region;
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

  componentDidUpdate(prevProps: LicenseViewProps) {
    const old = (prevProps.alpr && prevProps.alpr.images) || [];
    const newz = (this.props.alpr && this.props.alpr.images) || [];
    if (
      newz.length > old.length &&
      this.props.alpr &&
      this.licenseFromProps === "" &&
      this.licenseStateFromProps === ""
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

  processAlpr({ image, original }: { image: any; original: any }) {
    const { results } = this.props.alpr.alprResult;
    const images = results.map((result: any) => {
      const { plate, confidence, region, candidates, coordinates } = result;
      plate.region = region;
      const plates = candidates
        .sort((a: any, b: any) => b.confidence - a.confidence)
        .filter((thing: any, index: number) => {
          return (
            index ===
            candidates.findIndex((obj: any) => {
              return obj.plate === thing.plate;
            })
          );
        });
      const topLeft = coordinates[0];
      const topRight = coordinates[1];
      const bottomLeft = coordinates[2];
      const bottomRight = coordinates[3];

      const originX = Math.trunc((topLeft.x / image.width) * original.width);
      const originY = Math.trunc((topLeft.y / image.height) * original.height);
      const width =
        Math.trunc((topRight.x / image.width) * original.width) - originX;
      const height =
        Math.trunc((bottomRight.y / image.height) * original.height) - originY;

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
    // console.log('images length', images.length)
    Promise.all(
      images.map((x: any) => {
        return new Promise((resolve, reject) => {
          x.imageAsync
            .then((image: any) => {
              const resp = {
                plates: x.plates,
                image: Object.assign(image, { url: image.uri })
              };
              resolve(resp);
            })
            .catch((f: unknown) => {
              reject(f);
            });
        });
      })
    )
      .then((res: any[]) => {
        this.setState({ plates: res });
      })
      .catch(() => { });
  }

  clear() {
    this.setState(this.initialState);
  }

  processImage({ image, original }: { image: any; original: any }) {
    this.processAlpr({ image, original });
  }

  _onPlateSelected(selected: any) {
    this.setState({
      selected: selected,
      licensePlate: selected.candidate.plate,
      licenseState: selected.plate.region,
      showPlatePicker: false
    });
    if (this.props.onPlateSelected) {
      this.props.onPlateSelected(selected);
    }
  }

  toDegrees(rad: number) {
    return rad * (180 / Math.PI);
  }

  angleOf(x1: number, y1: number, x2: number, y2: number) {
    const deltaY = y1 - y2;
    const deltaX = x2 - x1;
    const result = this.toDegrees(Math.atan2(deltaY, deltaX));
    return result;
  }

  toRadians(ang: number) {
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
        <View>
          <Input
            placeholder="ie: T64353"
            autoCapitalize="characters"
            onChangeText={licensePlate => {
              this.setState({ licensePlate: licensePlate.toUpperCase() });
              this._onPlateSelected({
                plate: { region: this.state.licenseState },
                candidate: {
                  plate: licensePlate,
                },
                media: undefined
              });
            }}
            label={"License Plate"}
            value={this.licenseFromProps}
          />
          <Input
            placeholder="ie: NY"
            autoCapitalize="characters"
            onChangeText={licenseState => {
              this.setState({ licenseState: licenseState.toUpperCase() });
              this._onPlateSelected({
                plate: { region: licenseState },
                candidate: {
                  plate: this.state.licensePlate,
                },
                media: undefined
              });
            }}
            label={"State"}
            value={this.licenseStateFromProps}
          />
        </View>

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
                      {plate.plates.map((x: any) => {
                        return (
                          <View key={x.plate} style={styles.plateTextContainer}>
                            <Button
                              type="outline"
                              containerStyle={styles.buttonContainer}
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
