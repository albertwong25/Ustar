package com.hkust.ustar;

public abstract class LuminanceSource {

  private final int width;
  private final int height;

  protected LuminanceSource(int width, int height) {
    this.width = width;
    this.height = height;
  }

  /**
   * Fetches one row of luminance data from the underlying platform's bitmap.
   */
  public abstract byte[] getRow(int y, byte[] row);

  /**
   * Fetches luminance data for the underlying bitmap.
   */
  public abstract byte[] getMatrix();

  /**
   * @return The width of the bitmap.
   */
  public final int getWidth() {
    return width;
  }

  /**
   * @return The height of the bitmap.
   */
  public final int getHeight() {
    return height;
  }

  /**
   * @return Whether this subclass supports cropping.
   */
  public boolean isCropSupported() {
    return true;
  }

  /**
   * Returns a new object with cropped image data.
   */
  public LuminanceSource crop(int left, int top, int width, int height) {
    throw new RuntimeException("This luminance source does not support cropping.");
  }

  /**
   * @return Whether this subclass supports counter-clockwise rotation.
   */
  public boolean isRotateSupported() {
    return false;
  }

  /**
   * Returns a new object with rotated image data.
   */
  public LuminanceSource rotateCounterClockwise() {
    throw new RuntimeException("This luminance source does not support rotation.");
  }

}
