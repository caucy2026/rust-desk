class FileTransferSpacePolicy {
  static int maximumTransferBytes(int availableBytes) => availableBytes ~/ 2;

  static bool allows({
    required int transferBytes,
    required int availableBytes,
  }) {
    if (transferBytes < 0 || availableBytes < 0) return false;
    return transferBytes <= maximumTransferBytes(availableBytes);
  }
}
