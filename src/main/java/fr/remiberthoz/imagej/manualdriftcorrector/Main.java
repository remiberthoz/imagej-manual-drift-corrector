package fr.remiberthoz.imagej.manualdriftcorrector;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.gui.Roi;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.IJ;

public class Main implements PlugIn {

    ImagePlus im;

    public void run(String arg) {

        im = IJ.getImage();
        if (im.getNFrames() <= 1) {
            IJ.error("Manual Centor", "This plugin works on stacks with multiple frames (see your stack dimensions in 'Image > Properties...')");
            return;
        }

        GenericDialog dialog = new NonBlockingGenericDialog("Manual centor: definition of ROI");
        dialog.addMessage("Create ROI(s) around the object(s) of interest.\nThen select your ROI(s) in the ROI Manager and click 'Done'.");
        dialog.addRadioButtonGroup("ROI(s) are drawn on the:", new String[] {"first frame", "last frame"}, 1, 2, "last frame");
        dialog.setOKLabel("Done");
        dialog.showDialog();
        if (!dialog.wasOKed())
            return;

        boolean roisOnFirstFrame = dialog.getNextRadioButton().equals("first frame");

        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            IJ.error("No ROIs defined.");
            return;
        }
        Roi[] rois = rm.getSelectedRoisAsArray();
        if (rois.length == 0) {
            IJ.error("No ROIs defined.");
            return;
        }

        dialog = new NonBlockingGenericDialog("Manual centor");
        dialog.addMessage("Track a fixed object in your movie using the 'Point' tool.\nOn each frame: click at the position of the object and press 'M' to save in the ResultsTable.");
        dialog.setOKLabel("Done");
        dialog.showDialog();
        if (!dialog.wasOKed())
            return;

        ResultsTable rt = ResultsTable.getActiveTable();
        double[] x = rt.getColumn("X");
        double[] y = rt.getColumn("Y");
        if (x == null || y == null) {
            IJ.error("Table is not properly formatted: should contain columns 'X' and 'Y'.");
            return;
        }
        if (x.length != im.getNFrames() || y.length != im.getNFrames()) {
            IJ.error("Image number of frames and table number of rows are different.");
            return;
        }

        double x0 = x[roisOnFirstFrame ? 0 : im.getNFrames()-1];
        double y0 = y[roisOnFirstFrame ? 0 : im.getNFrames()-1];

        for (int i = 0; i < rois.length; i++) {
            Roi roiref = rois[i];
            int xref = (int) roiref.getXBase();
            int yref = (int) roiref.getYBase();
            int w = (int) roiref.getFloatWidth();
            int h = (int) roiref.getFloatHeight();
            ImageStack stack = new ImageStack(w, h);
            double pixcal = im.getCalibration().pixelWidth;
            for (int t = 0; t < im.getNFrames(); t++) {
                for (int c = 0; c < im.getNChannels(); c++) {
                    im.setT(t+1);
                    im.setC(c+1);
                    Roi roi = new Roi(xref + (x[t] - x0)/pixcal, yref + (y[t] - y0)/pixcal, w, h);
                    im.setRoi(roi);
                    ImageProcessor ip = im.getProcessor().crop();
                    stack.addSlice(ip);
                }
            }
            ImagePlus output = new ImagePlus(im.getShortTitle() + "_" + (i+1), stack);
            output.show();
        }

        RoiManager saveRm = new RoiManager(false);
        for (Roi roi : rois) {
            saveRm.addRoi(roi);
        }
        dialog = new GenericDialog("Manual centor");
        dialog.addFileField("Filename for ROI(s)", im.getOriginalFileInfo().getFilePath() + "_rois.zip");
        dialog.addFileField("Filename for tracking", im.getOriginalFileInfo().getFilePath() + "_tracking.csv");
        dialog.addMessage("Image file(s) are not save automatically! Sorry.");
        dialog.hideCancelButton();
        dialog.showDialog();
        if (!dialog.wasOKed())
            return;
        String roisFilename = dialog.getNextString();
        saveRm.save(roisFilename);
        String trackingFilename = dialog.getNextString();
        rt.save(trackingFilename);
    }
}
