import {
  StyleSheet
} from 'react-native';

export const ErrorStyle = StyleSheet.create({
  style: {
    color: 'red'
  }
});

export const ButtonContainerStyle = StyleSheet.create({
  style: {
    position: 'absolute',
    flex: 1,
    width: '100%',
    justifyContent: 'center',
    alignItems: 'center',
    bottom: '40%',
    padding: 0,
  },
  bottom: {
    flex: 1,
    position: 'absolute',
    width: '100%',
    bottom: 0,
    padding: 0
  }
});

export const ButtonStyle = StyleSheet.create({
  style: {
    backgroundColor: '#ec682caa',
    alignItems: 'center',
    width: '100%'
  },
  fill: {
    backgroundColor: '#ec682cFF',
    height: 55,
    width: '100%'
  }
});
