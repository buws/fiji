package fiji.plugin.trackmate.tests;

import java.io.File;

import org.scijava.util.AppUtils;

import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.features.track.TrackBranchingAnalyzer;
import fiji.plugin.trackmate.io.TmXmlReader;

public class TrackBranchingAnalyzer_Test {

	public static void main(final String[] args) {

		// Load

		final File file = new File(AppUtils.getBaseDirectory(TrackMate.class), "samples/FakeTracks.xml");
		final TmXmlReader reader = new TmXmlReader(file);

		// Analyze
		final TrackBranchingAnalyzer analyzer = new TrackBranchingAnalyzer(reader.getModel());
		analyzer.process(reader.getModel().getTrackModel().trackIDs(false));
		System.out.println("Analysis done in " + analyzer.getProcessingTime() + " ms.");

	}

}
