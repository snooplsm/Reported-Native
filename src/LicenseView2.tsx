import React from 'react';
import { StyleSheet, Modal, ScrollView, View, Text } from 'react-native';
import { Input, Image, Button } from 'react-native-elements';

interface ILicenseView {
    alpr: any;
    alprImage: any;
    licensePlate: any;
    onPlateSelected: (any) => void;
};

export const LicenseView: React.FC<ILicenseView> = ({
    alpr, alprImage, licensePlate,
    onPlateSelected,
}) => {
    const [licPlate, setLicPlate] = React.useState<string>(licensePlate && licensePlate.plate);
    const [licState, setLicState] = React.useState<string>(licensePlate && licensePlate.region);
    const [showPlatePicker, setShowPlatePicker] = React.useState<boolean>(true);

    const plates = alpr && alpr.results;

    const _onPlateSelected = ({ plate }) => {
        setShowPlatePicker(false);
        if (!!plate) {
            const myPlate = plate.plate.toUpperCase();
            const myRegion = plate.region.toUpperCase();
            setLicPlate(myPlate);
            setLicState(myRegion);
            onPlateSelected({
                plate: myPlate,
                region: myRegion,
            });
        }
    }

    const _onChange = ({ key, value }) => {
        const valueU = value.toUpperCase();
        switch (key) {
            case 'plate':
                setLicPlate(valueU);
                _onPlateSelected({
                    plate: {
                        plate: valueU,
                        region: licState,
                    }
                });
                break;
            case 'region':
                setLicState(value.toUpperCase());
                _onPlateSelected({
                    plate: {
                        plate: licPlate,
                        region: valueU,
                    }
                });
                break;
            default:
                break;
        }
    }

    return (
        <>
            <View>
                <Input
                    placeholder='ie: T64353'
                    autoCapitalize='characters'
                    onChangeText={value => {
                        _onChange({ key: 'plate', value: value.toUpperCase() });
                    }}
                    label={'License Plate'}
                    value={licPlate}
                />
                <Input
                    placeholder='ie: NY'
                    autoCapitalize='characters'
                    onChangeText={value => {
                        _onChange({ key: 'region', value: value.toUpperCase() });
                    }}
                    label={'State'}
                    value={licState}
                />
            </View>

            <View style={styles.container}>
                <Modal
                    visible={!!plates && !!plates.length && showPlatePicker}
                    animationType='slide'
                    onRequestClose={() => setShowPlatePicker(false)}
                >
                    <View style={styles.modalContainer}>
                        <View style={styles.modalContent}>
                            <ScrollView>
                                <View style={{ alignItems: 'center' }}>
                                    <Text style={styles.modalHeader}>
                                        Choose plate if applicable
                                    </Text>
                                    {!!alprImage && <Image
                                        style={styles.plateImage} source={{ uri: alprImage.uri }}
                                        resizeMode='contain'
                                    />}
                                    <View>
                                        <View>
                                            {!!plates && plates.map((plate, idx) => (
                                                <View key={idx} style={styles.modalPlateContainer}>
                                                    <Button
                                                        type='outline'
                                                        onPress={() =>
                                                            _onPlateSelected({
                                                                plate: plate,
                                                            })
                                                        }
                                                        title={`${plate.region.toUpperCase()} - ${plate.plate.toUpperCase()} (${parseInt(plate.confidence)}%)`}
                                                    />
                                                </View>
                                            ))}

                                            <Button
                                                style={styles.modalCloseButton}
                                                onPress={() => setShowPlatePicker(false)}
                                                title='No match'
                                            />
                                        </View>
                                    </View>
                                </View>
                            </ScrollView>
                        </View>
                    </View>
                </Modal>
            </View>
        </>
    )
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
    },

    modalContainer: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
    },
    modalContent: {
        backgroundColor: 'white',
        padding: 20,
        borderRadius: 10,
        width: '80%',
        maxHeight: '80%',
    },
    modalHeader: {
        paddingBottom: 30,
        fontSize: 20,
    },
    modalCloseButton: {
        paddingTop: 20,
        paddingBottom: 10,
    },
    plateImage: {
        width: '100%',
        height: undefined,
        aspectRatio: 1,
        display: 'none',
    },
    modalPlateContainer: {
        paddingBottom: 20,
    },
});
