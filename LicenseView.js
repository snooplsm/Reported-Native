import React from 'react';
import { StyleSheet, TouchableOpacity, TouchableWithoutFeedback, Text, View, Modal } from 'react-native';
import { Button, Icon, Input, Image } from 'react-native-elements'
import { ImageManipulator, ImagePicker, Permissions } from 'expo';
import { AutoStyle } from './Styles'
import { alpr } from './Api'
import { result } from './alpr'

export default class LicenseViewModal extends React.Component {

  constructor(props) {
    super(props)
    this.state = {
      licenses: [],
      result: result,
      plates: [],
      licensePlate: '',
      showPlatePicker: true
    }
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    const old = prevProps.images || []
    const newz = this.props.images || []
    if(old.length!==newz.length) {
      this.processImage(newz[newz.length-1])
      return true
    } else {
      return false
    }
  }

  processAlpr(image) {
    const { width, height, results }  = this.state.result
    const images = results.map(result=> {
      const {plate,confidence,region,candidates,coordinates} = result
      const plates = candidates.sort((a,b) => b.confidence-a.confidence).filter((thing,index)=> {
        return index === candidates.findIndex(obj => {
          return obj.plate === thing.plate
        });
      })
      const topLeft = coordinates[0]
      const topRight = coordinates[1]
      const bottomLeft = coordinates[2]
      const bottomRight = coordinates[3]
      const originX = parseInt(topLeft.x/image.width * image.width)
      const originY = parseInt(topLeft.y/image.height * image.height)
      const width = parseInt(topRight.x/image.width * image.width) - originX
      const height = parseInt(bottomRight.y/image.height * image.height) -originY
      const crop = {
        originX: originX,
        originY: originY,
        width: width,
        height: height
      }
      //console.log("cropping",crop)
      //console.log("image ", image.width,image.height)
      return {
        plates: plates,
        imageAsync: ImageManipulator.manipulateAsync(image.url, [
        {
          crop: crop
        }
      ])
    }})
    //console.log('images length', images.length)
    Promise.all(images.map((x) => {
      return new Promise((resolve, reject)=> {
        x.imageAsync.then(image=> {
          const resp = {plates: x.plates, image: image}
          resolve(resp)
        }).catch(f=> {
          reject(f)
        })
      })
    })).then(res=> {
      this.setState({plates: res})
    }).catch(ex=> {
    })
  }


  processImage(image) {
    this.processAlpr(image)
  }

  _onPlateSelected(selected) {
    //console.log(selected)
    this.setState({
      selected: selected,
      licensePlate: selected.candidate.plate,
      showPlatePicker: false
    })
    if(this.props.onPlateSelected) {
      this.props.onPlateSelected(selected)
    }
  }

  render() {
    return (
      <View style={{backgroundColor: 'red'}}>
        {this.state.plates.map(plate=>
          <Image
            source={plate.image} />
        )}
        <Input
          placeholder='License Number or Medallion'
          onChangeText={licensePlate=>this.setState({licensePlate: licensePlate.toUpperCase()}) }
          label={this.state.licensePlate.length==0 ? "" : 'License Number or Medallion'}
          value={this.state.licensePlate}/>

          <Modal visible={this.state.plates.length!=0 && this.state.showPlatePicker}>
            <View style={styles.container}>
            {this.state.plates.map(plate=> {
              return (
                  <><Image style={styles.plateImage} source={plate.image} />
                  <Text style={styles.header}>We may have detected the license plate, please choose from the following if applicable.</Text>
                  <View style={styles.plateContainer}>
                  {plate.plates.map(x=>{
                    return (
                      <View style={styles.plateTextContainer}>
                        <Text style={styles.plateText}>{x.plate} ({x. confidence.toFixed(1)})</Text>
                        <Button
                         onPress={() => this._onPlateSelected({plate:plate, candidate: x})}
                         icon={
                          <Icon
                            name="check"
                            size={12}
                            type="material"
                            color="white"
                          />}/>
                      </View>)
                  })}
                  <Button onPress={()=> this.setState({showPlatePicker:false})} title='None of these match'/>
                  </View>
                </>
              )
            })}
            </View>
          </Modal>
      </View>)
  }
}

const styles = StyleSheet.create({
  plateImage: {

  },
  button: {
    width: '30%',
    height: 60
  },
  header: {
    padding: 20,

  },
  plateContainer: {
    flexDirection: 'column',
  },
  plateTextContainer: {
    flexDirection: 'row'
  },
  plateText: {
    fontSize: 18
  },
  container: {
    flex: 1,
    width: '100%',
    justifyContent: 'center',
    alignItems: 'center',
    flexDirection: 'column'
  },
  imageViewer: {
    backgroundColor: 'yellow',
    width: 200,
    height: 200
  },
  modalContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
}});
