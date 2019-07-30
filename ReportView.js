import React from "react";

import { StyleSheet, Text, View } from "react-native";
import { Card, Icon } from "react-native-elements";
import Autolink from "react-native-autolink";
import { HorizontalStyle, ButtonStyle } from "./Styles";
import ImageCarousel from "./ImageCarousel";
import moment from "moment";

export default class ReportView extends React.Component {
  constructor(props) {
    super(props);
  }
  carousel() {
    const { report: rpt } = this.props;
    const { report, address } = rpt;
    if (report.media && report.media.length > 0) {
      return (
        <ImageCarousel
          onItemPress={x => {
            //console.log(x);
            alert("ok");
          }}
          entries={report.media.map(x => {
            const img = {};
            console.log("media", x);
            if (x.type === "YOUTUBE") {
              const thumb = x.thumbnails[0];
              img.url = thumb.url;
              img.width = thumb.width;
              img.height = thumb.height;
            } else {
              const thumb =
                x.thumbnails.filter(x => {
                  return x.width === x.height && x.width == 512;
                })[0] ?? {};
              img.url = x.url;
              img.width = x.width;
              img.height = x.height;
            }
            console.log("using", img);
            return img;
          })}
        />
      );
    } else {
      return <></>;
    }
  }

  closeModal() {
    this.setState({ imageModalImages: false });
  }

  imageModal() {
    if (this.state.imageModalImages) {
      return (
        <Modal visible={true} onRequestClose={() => {}} transparent={false}>
          <ImageViewer
            onClick={() => this.closeModal()}
            imageUrls={this.state.imageModalImages}
          />
        </Modal>
      );
    } else {
      return <></>;
    }
  }

  get status() {
    const { report: rpt } = this.props;
    const { report, address } = rpt;
    if (report.status <= 0) {
      return (
        <View>
          <Text>[PENDING]</Text>
        </View>
      );
    }
  }
  render() {
    const { report: rpt } = this.props;
    console.log(report);
    const { report, address } = rpt;
    const time = moment(report.timeofincident);
    return (
      <Card title={report.complaint}>
        <View
          style={[
            HorizontalStyle.style,
            {
              paddingBottom: 10
            }
          ]}
        >
          <Text>
            {address.building} {address.street}
          </Text>
          <Text style={ButtonStyle.orangeText}>{report.license.plate}</Text>
        </View>
        {this.carousel()}
        <Autolink text={report.description} />
        <View
          style={[
            HorizontalStyle.style,
            {
              paddingTop: 10
            }
          ]}
        >
          <Text>{` ${time.fromNow()} \n ${time.format(
            "M/D/YY h:mm A"
          )} `}</Text>
          <Text style={ButtonStyle.orangeText}>
            {address.cb ? `#CB${address.cb}` : ""}
          </Text>
        </View>
      </Card>
    );
  }
}
