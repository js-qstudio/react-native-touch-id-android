import { NativeModules } from 'react-native';

const Fingerprint = NativeModules.Fingerprint;

export default {
  getNativeModule() {
    return Fingerprint;
  },
}
