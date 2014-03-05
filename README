This tool is more or less an experiment.

Goal: create a copy of an ODT file with all images resampled to an appropriate size for printing. This is mainly useful if your images are very high resolution (something like 600dpi) but for example you want to produce a printable pdf at 90dpi, or put it online at only 150dpi, etc.

How to use:
1- Start the program (duh!)
2- Use File->Open
3- Set dpi and jpeg quality
4- File->Save. It *should* work on most files. As a PoC, it doesn't understand *all* the OpenDocument format, but it was tested on relatively large and complex files with success.

Options:
- Target DPI: the output DPI you want. Pretty straightforward.
- JPEG quality: the lower the smaller the file, but the uglier the images.
- Kill transparency: jpeg can't handle transparency. If you leave this option unchecked, any image containing an alpha channel will be saved as PNG instead. Otherwise, everything goes in jpeg. This can produce strange result, as transparent pixels will not systematically come out white for example.

How it works:
After extracting the ODT file, the content is parsed to retrieve the intended pictures size (stored in cm). Using this information, and the DPI level set, the appropriate size in pixel is computed. If it is smaller than the original image, it is used; otherwise the original is kept (images are never enlarged).
When saving, images are compressed in both jpeg and png (png only for transparent images), and the smallest one is stored in the resulting file. All image references are updated in both content and styles xml files.

This code is put under an MIT license; this means no guarantees, but if it turns out to be useful, you can use it. Just state it's origin somewhere.