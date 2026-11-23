// macOS 14.2+: record only the named synthetic simulator process's actual output.
// Usage: capture-audio CORE_AUDIO_PROCESS_ID OUTPUT.wav STOP_FILE
// Creates a private, unmuted Core Audio tap; no microphone or other-app audio.
import Foundation
import CoreAudio
import AVFoundation

func checked(_ status: OSStatus, _ operation: String) throws {
    if status != noErr { throw NSError(domain: operation, code: Int(status)) }
}
let process = AudioObjectID(CommandLine.arguments[1])!
let output = URL(fileURLWithPath: CommandLine.arguments[2])
let stopFile = CommandLine.arguments[3]
let description = CATapDescription(stereoMixdownOfProcesses: [process])
description.uuid = UUID()
description.name = "Normal Bias synthetic simulator trial"
description.isPrivate = true
description.muteBehavior = .unmuted
var tap = AudioObjectID(kAudioObjectUnknown)
try checked(AudioHardwareCreateProcessTap(description, &tap), "Create process tap")
defer { AudioHardwareDestroyProcessTap(tap) }
var address = AudioObjectPropertyAddress(mSelector: kAudioTapPropertyFormat, mScope: kAudioObjectPropertyScopeGlobal, mElement: kAudioObjectPropertyElementMain)
var asbd = AudioStreamBasicDescription()
var size = UInt32(MemoryLayout<AudioStreamBasicDescription>.size)
try checked(AudioObjectGetPropertyData(tap, &address, 0, nil, &size, &asbd), "Read tap format")
let format = AVAudioFormat(streamDescription: &asbd)!
let file = try AVAudioFile(forWriting: output, settings: format.settings, commonFormat: format.commonFormat, interleaved: format.isInterleaved)
let deviceDescription: [String: Any] = [
    kAudioAggregateDeviceNameKey: "Normal Bias private trial tap",
    kAudioAggregateDeviceUIDKey: UUID().uuidString,
    kAudioAggregateDeviceIsPrivateKey: true,
    kAudioAggregateDeviceTapAutoStartKey: true,
    kAudioAggregateDeviceTapListKey: [[kAudioSubTapUIDKey: description.uuid.uuidString, kAudioSubTapDriftCompensationKey: true]]
]
var device = AudioObjectID(kAudioObjectUnknown)
try checked(AudioHardwareCreateAggregateDevice(deviceDescription as CFDictionary, &device), "Create private aggregate")
defer { AudioHardwareDestroyAggregateDevice(device) }
var procedure: AudioDeviceIOProcID?
let queue = DispatchQueue(label: "normalbias.audio-trial.capture")
try checked(AudioDeviceCreateIOProcIDWithBlock(&procedure, device, queue) { _, input, _, _, _ in
    guard let buffer = AVAudioPCMBuffer(pcmFormat: format, bufferListNoCopy: input) else { return }
    do { try file.write(from: buffer) } catch { fputs("Audio sample write failed\n", stderr) }
}, "Create capture procedure")
defer { AudioDeviceDestroyIOProcID(device, procedure!) }
try checked(AudioDeviceStart(device, procedure), "Start tap")
defer { AudioDeviceStop(device, procedure) }
print("Recording actual simulator process audio at \(Date().timeIntervalSince1970)")
fflush(stdout)
while !FileManager.default.fileExists(atPath: stopFile) { Thread.sleep(forTimeInterval: 0.1) }
print("Stopped at \(Date().timeIntervalSince1970)")
