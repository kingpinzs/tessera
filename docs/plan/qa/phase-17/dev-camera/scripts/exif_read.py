#!/usr/bin/env python3
"""Prints a JPEG's EXIF as `Name=value` words (exiftool is not on this host): the tags the rows read."""
import sys
from PIL import Image, ExifTags

im = Image.open(sys.argv[1])
exif = im.getexif()
out = {}
for tag, value in exif.items():
    out[ExifTags.TAGS.get(tag, str(tag))] = value
for tag, value in exif.get_ifd(ExifTags.IFD.Exif).items():
    out[ExifTags.TAGS.get(tag, str(tag))] = value
gps = exif.get_ifd(ExifTags.IFD.GPSInfo)
for tag, value in gps.items():
    out['GPS:' + ExifTags.GPSTAGS.get(tag, str(tag))] = value
want = ['Orientation', 'DateTimeOriginal', 'DateTime', 'Make', 'Model', 'ISOSpeedRatings', 'ExposureTime', 'WhiteBalance',
        'SubjectDistance', 'ExifImageWidth', 'ExifImageHeight', 'Software', 'GPS:GPSLatitude', 'GPS:GPSLatitudeRef', 'GPS:GPSLongitude', 'GPS:GPSLongitudeRef']
print(' '.join('%s=%s' % (k, str(out[k]).replace(' ', '')) for k in want if k in out) + ' size=%dx%d gps_tags=%d' % (im.width, im.height, len(gps)))
