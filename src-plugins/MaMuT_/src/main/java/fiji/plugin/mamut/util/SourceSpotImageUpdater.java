package fiji.plugin.mamut.util;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.Max;
import net.imglib2.algorithm.stats.Min;
import net.imglib2.display.RealUnsignedByteConverter;
import net.imglib2.display.XYProjector;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.position.transform.Round;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import viewer.render.Source;
import viewer.render.SourceAndConverter;
import viewer.util.Affine3DHelpers;

import com.mxgraph.util.mxBase64;

import fiji.plugin.mamut.feature.spot.SpotSourceIdAnalyzerFactory;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.visualization.trackscheme.SpotImageUpdater;

public class SourceSpotImageUpdater<T extends RealType<T>> extends SpotImageUpdater {

	/** How much extra we capture around spot radius. */
	private static final double RADIUS_FACTOR = 1.1;
	private final List<SourceAndConverter<T>> sources;
	private final ThreadGroup threadGroup;

	public SourceSpotImageUpdater(final Settings settings, final List<SourceAndConverter<T>> sources) {
		super(settings);
		this.sources = sources;
		this.threadGroup = new ThreadGroup("Source spot image grabber threads");
	}

	/**
	 * Returns the image string of the given spot, based on the raw images
	 * contained in the given model. For performance, the image at target frame
	 * is stored for subsequent calls of this method. So it is a good idea to
	 * group calls to this method for spots that belong to the same frame.
	 */
	@Override
	public String getImageString(final Spot spot) {
		final StringBuffer str = new StringBuffer();

		final Thread th = new Thread(threadGroup, "Spot Image grabber for " + spot) {

			@Override
			public void run() {

				// Retrieve frame
				final int frame = spot.getFeature(Spot.FRAME).intValue();
				// Retrieve source ID
				final Double si = spot.getFeature(SpotSourceIdAnalyzerFactory.SOURCE_ID);
				if (null == si) {
					return;
				}

				final int sourceID = si.intValue();
				final Source<T> source = sources.get(sourceID).getSpimSource();
				final RandomAccessibleInterval<T> img = source.getSource(frame, 0);

				// Get spot coords
				final AffineTransform3D sourceToGlobal = source.getSourceTransform(frame, 0);
				final Point roundedSourcePos = new Point(3);
				sourceToGlobal.applyInverse(new Round<Point>(roundedSourcePos), spot);
				final long x = roundedSourcePos.getLongPosition(0);
				final long y = roundedSourcePos.getLongPosition(1);
				final long z = roundedSourcePos.getLongPosition(2);
				final long r = (long) Math.ceil(RADIUS_FACTOR * spot.getFeature(Spot.RADIUS).doubleValue() / Affine3DHelpers.extractScale(sourceToGlobal, 0));

				// Extract central slice
				final IntervalView<T> slice = Views.hyperSlice(img, 2, z);

				// Crop
				final Interval cropInterval = Intervals.intersect(slice, Intervals.createMinMax(x - r, y - r, x + r, y + r));

				final BufferedImage image;
				if (isEmpty(cropInterval))
					image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
				else {
					final IntervalView<T> crop = Views.zeroMin(Views.interval(slice, cropInterval));
					final int width = (int) crop.dimension(0);
					final int height = (int) crop.dimension(1);
					image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
					final byte[] imgData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
					final double minValue = Min.findMin(Views.iterable(crop)).get().getRealDouble();
					final double maxValue = Max.findMax(Views.iterable(crop)).get().getRealDouble();
					new XYProjector<T, UnsignedByteType>(crop, ArrayImgs.unsignedBytes(imgData, width, height), new RealUnsignedByteConverter<T>(minValue, maxValue)).map();
				}

				// Convert to string
				final ByteArrayOutputStream bos = new ByteArrayOutputStream();
				String baf;
				try {
					ImageIO.write(image, "png", bos);
					baf = mxBase64.encodeToString(bos.toByteArray(), false);
				} catch (final IOException e) {
					e.printStackTrace();
					baf = "";
				}
				str.append(baf);
			}

		};

		th.start();
		try {
			th.join();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		return str.toString();
	}

	private static final boolean isEmpty(final Interval interval) {
		final int n = interval.numDimensions();
		for (int d = 0; d < n; ++d)
			if (interval.min(d) > interval.max(d))
				return true;
		return false;
	}

}