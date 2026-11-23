import Foundation
import CoreAudio
var address = AudioObjectPropertyAddress(mSelector: kAudioHardwarePropertyProcessObjectList, mScope: kAudioObjectPropertyScopeGlobal, mElement: kAudioObjectPropertyElementMain)
var size: UInt32 = 0
let system = AudioObjectID(kAudioObjectSystemObject)
AudioObjectGetPropertyDataSize(system, &address, 0, nil, &size)
var processes = [AudioObjectID](repeating: 0, count: Int(size)/MemoryLayout<AudioObjectID>.size)
AudioObjectGetPropertyData(system, &address, 0, nil, &size, &processes)
for process in processes {
 var pid: pid_t = 0; var psize = UInt32(MemoryLayout<pid_t>.size)
 var pa = AudioObjectPropertyAddress(mSelector: kAudioProcessPropertyPID, mScope: kAudioObjectPropertyScopeGlobal, mElement: kAudioObjectPropertyElementMain)
 AudioObjectGetPropertyData(process, &pa, 0, nil, &psize, &pid)
 var bundle: Unmanaged<CFString>?; var bsize = UInt32(MemoryLayout<Unmanaged<CFString>?>.size)
 pa.mSelector = kAudioProcessPropertyBundleID
 let status = withUnsafeMutablePointer(to: &bundle) {
  AudioObjectGetPropertyData(process, &pa, 0, nil, &bsize, UnsafeMutableRawPointer($0))
 }
 guard status == noErr, let owned = bundle else { continue }
 // AudioHardware.h specifies that the caller releases the returned CFObject.
 let identifier = owned.takeRetainedValue() as String
 if identifier == "dev.normalbias.client" {
  print(process, pid, identifier)
 }
}
