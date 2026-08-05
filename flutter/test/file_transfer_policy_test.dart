import 'package:flutter_hbb/models/file_model.dart';
import 'package:flutter_hbb/models/file_transfer_policy.dart';
import 'package:flutter_hbb/models/transfer_history_model.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('FileTransferSpacePolicy', () {
    test('allows exactly half and rejects anything larger', () {
      expect(
        FileTransferSpacePolicy.allows(
          transferBytes: 500,
          availableBytes: 1000,
        ),
        isTrue,
      );
      expect(
        FileTransferSpacePolicy.allows(
          transferBytes: 501,
          availableBytes: 1000,
        ),
        isFalse,
      );
    });

    test('fails closed for invalid measurements', () {
      expect(
        FileTransferSpacePolicy.allows(
          transferBytes: 1,
          availableBytes: -1,
        ),
        isFalse,
      );
    });
  });

  group('ReceivedLocalFileRegistry', () {
    late ReceivedLocalFileRegistry registry;
    late Entry entry;

    setUp(() {
      registry = ReceivedLocalFileRegistry(persistOverride: (_) {});
      entry = Entry()
        ..entryType = 4
        ..name = 'received.txt'
        ..path = '/storage/emulated/0/Download/received.txt'
        ..size = 12
        ..modifiedTime = 100;
      registry.records[entry.path] = ReceivedLocalFileRecord(
        path: entry.path,
        size: entry.size,
        modifiedTime: entry.modifiedTime,
        isDirectory: false,
        completedAt: 200,
      );
    });

    test('allows an unchanged successfully received file', () {
      expect(registry.canDelete(entry), isTrue);
    });

    test('rejects local files without a received-file record', () {
      entry.path = '/storage/emulated/0/Download/original.txt';
      expect(registry.canDelete(entry), isFalse);
    });

    test('rejects files changed after transfer', () {
      entry.modifiedTime = 101;
      expect(registry.canDelete(entry), isFalse);
    });

    test('allows a recorded unchanged received folder', () {
      entry.entryType = 0;
      registry.records[entry.path] = ReceivedLocalFileRecord(
        path: entry.path,
        size: entry.size,
        modifiedTime: entry.modifiedTime,
        isDirectory: true,
        completedAt: 200,
      );
      expect(registry.canDelete(entry), isTrue);
    });

    test('rejects an unrecorded local folder', () {
      entry
        ..entryType = 0
        ..path = '/storage/emulated/0/Download/original-folder';
      expect(registry.canDelete(entry), isFalse);
    });

    test('forgetTree revokes a folder and recorded descendants', () {
      entry.entryType = 0;
      registry.records[entry.path] = ReceivedLocalFileRecord(
        path: entry.path,
        size: entry.size,
        modifiedTime: entry.modifiedTime,
        isDirectory: true,
        completedAt: 200,
      );
      registry.records['${entry.path}/child.txt'] = ReceivedLocalFileRecord(
        path: '${entry.path}/child.txt',
        size: 1,
        modifiedTime: 100,
        completedAt: 200,
      );

      registry.forgetTree(entry.path);

      expect(registry.records, isEmpty);
    });
  });
}
