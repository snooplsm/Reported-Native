import React from 'react';
import {Text, TouchableOpacity, View, StyleSheet} from 'react-native';
import ComplaintView from './ComplaintView'
import { ImagePicker, Permissions } from 'expo';
import { Button, Image } from 'react-native-elements'
import { Modal } from 'react-native';
import ImageViewer from 'react-native-image-zoom-viewer';
import { ImageManipulator } from 'expo';
import AddressView from './AddressView'
import LicenseView from './LicenseView'
import ImageCarousel from './ImageCarousel'
import moment from 'moment'
import DateTimePicker from "react-native-modal-datetime-picker";
import { alpr } from './Api'

export default class Submission extends React.Component {
  static navigationOptions = {
    title: 'Report'
  };

  constructor(props) {
    super(props)
    this.state = {
      images: [],
      resizedImages: [],
      datePickerVisible: true
    }
  }

  closeModal() {
    this.setState({imageModal: false})
  }

  imageModal() {
    if(this.state.imageModal) {
      return <Modal
        visible={true}
        onRequestClose={()=> {

        }}
        transparent={false}>
          <ImageViewer
            onClick={()=> this.closeModal()}
            imageUrls={this.state.images} />
            </Modal>
    } else {
      return (<></>)
    }
  }

  render() {
    return (
      <View style={styles.container}>
        <ComplaintView/>
        <Button
          onPress={this._pickImage}
          title={'Photo/Video'}/>
        <ImageCarousel entries={this.state.images} />
        {this.imageModal()}
        <LicenseView
          onPlateSelected={(plate)=> console.log('plate selected', plate)}
          images={this.state.images}
          />
        <AddressView onPress={(rowData)=> {
          console.log(rowData)
          this.setState({location: rowData})
        }}/>
        <DateTimePicker
          mode={'datetime'}
          titleIOS={"Time of incident"}
          isVisible={this.state.datePickerVisible}
          date={this.state.timeofreport}
          onConfirm={(date)=> {
            this.setState({
              timeofreport: date,
              datePickerVisible: false
            })
          }}
          onCancel={() => {
            this.setState({datePickerVisible: false})
          }}
        />
      </View>
    )
  }

  resizeImages = async()=> {
    const resizeAsync = this.state.images.map(x=> {
      let crop = null
      if(x.width<x.height) {
        crop = {
          originX: (x.height - x.width) / 2,
          originY: 0,
          width: x.width,
          height: x.width
        }
      } else {
        crop = {
          originY: (x.width - x.height) / 2,
          originX: 0,
          width: x.height,
          height: x.height
        }
      }
      return ImageManipulator.manipulateAsync(x.url, [
        {
          crop: crop
        },
        { resize: {
          width: 200,
          height: 200
        }}
      ])
    })
    return Promise.all(resizeAsync)
  }

  _pickImage = async ()=> {
    const permission = await Permissions.getAsync(Permissions.CAMERA_ROLL);
    const imageLaunch = ImagePicker.launchImageLibraryAsync({
        exif: true,
        mediaTypes: ImagePicker.MediaTypeOptions.All
      });
    const success = (result)=> {
      if(result.cancelled) {
        return
      }
      const { exif } = result
      const { width, height, uri, type } = result
      const {
        DateTimeOriginal: timeofreport,
        GPSAltitude:altitude,
        GPSLatitude:lat,
        GPSLongitude:lng } = exif
        console.log(exif)
        console.log(width,height,uri,type,timeofreport,altitude,lat,lng)
      console.log(timeofreport)
      if(timeofreport) {
        console.log('we have a time of report')
        var datetime = moment(timeofreport, "yyyy:MM:dd HH:mm:ss").toDate()
        console.log(datetime)
        this.setState({
          timeofreport: datetime,

        })
      }
      const image = {
        url: uri,
        width: width,
        height: height
      }
      console.log(timeofreport)
      const images = [...this.state.images, image]
      this.setState({images: images})
      this.resizeImages().then(x=> {
        this.setState({resizedImages:x})
      }).catch(x=> {
        console.error(x)
      })
    }
    if (permission.status !== 'granted') {
      const newPermission = await Permissions.askAsync(Permissions.CAMERA_ROLL);
      if (newPermission.status === 'granted') {
        imageLaunch.then(success)

      }
    } else {
      imageLaunch.then(success)
    }
  }
}

const styles = StyleSheet.create({
  button: {
    width: '30%',
    height: 60
  },
  container: {
    width: '100%',
    height: '100%',
  },
  imageViewer: {
    backgroundColor: 'yellow',
    width: 200,
    height: 200
  },
  backgroundVideo: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
}});
