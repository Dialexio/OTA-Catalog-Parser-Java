/*
 * OTA Catalog Parser 0.3.3
 * Copyright (c) 2015 Dialexio
 * 
 * The MIT License (MIT)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import com.dd.plist.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.*;
import org.xml.sax.SAXException;

public class Parser {
	private final static ArrayList<OTAPackage> ENTRY_LIST = new ArrayList<OTAPackage>();
	private final static HashMap<String, Integer> actualVersionRowspanCount = new HashMap<String, Integer>(),
		buildRowspanCount = new HashMap<String, Integer>(),
		dateRowspanCount = new HashMap<String, Integer>(),
		fileRowspanCount = new HashMap<String, Integer>(),
		marketingVersionRowspanCount = new HashMap<String, Integer>();
	private final static HashMap<String, HashMap<String, Integer>> prereqRowspanCount = new HashMap<String, HashMap<String, Integer>>(); // Build, <PrereqOS, count>

	private static boolean checkModel, showBeta = false;
	private static NSDictionary root = null;
	private static String device = "", maxOSVer = "", minOSVer = "", model = "";


	private static void addEntries(final NSDictionary PLIST_ROOT) {
		final NSObject[] ASSETS = ((NSArray)PLIST_ROOT.objectForKey("Assets")).getArray(); // Looking for the array with key "Assets."

		boolean matched;
		OTAPackage entry;

		// Look at every item in the array with the key "Assets."
		for (NSObject item:ASSETS) {
			entry = new OTAPackage((NSDictionary)item); // Feed it into our own object. This will be used for sorting in the future.
			matched = false;

			// Beta check.
			if (!showBeta && entry.isBeta())
				continue;

			// Device check.
			for (NSObject supportedDevice:entry.supportedDevices()) {
				if (device.equals(supportedDevice.toString())) {
					matched = true;
					break;
				}
			}

			// Model check, if needed.
			if (matched && checkModel) {
				matched = false; // Skipping unless we can verify we want it.

				// Make sure "SupportedDeviceModels" exists.
				if (entry.supportedDeviceModels() != null) {
					// Since it's an array, check each entry if the model matches.
					for (NSObject supportedDeviceModel:entry.supportedDeviceModels())
						if (supportedDeviceModel.toString().equals(model)) {
							matched = true;
							break;
						}
				}
			}

			// OS version check. Move to the next item if it doesn't match.
			if (matched && !maxOSVer.isEmpty() && (maxOSVer.compareTo(entry.marketingVersion()) < 0))
					continue;
			if (matched && !minOSVer.isEmpty() && (minOSVer.compareTo(entry.marketingVersion()) > 0))
					continue;

			// Add it after it survives the checks.
			if (matched)
				ENTRY_LIST.add(entry);
		}
	}

	private static void countRowspan(final ArrayList<OTAPackage> ENTRYLIST) {
		HashMap<String, Integer> prereqNestedCount;

		// Count the rowspans for wiki markup.
		for (OTAPackage entry:ENTRYLIST) {
			prereqNestedCount = new HashMap<String, Integer>();

			// Actual version
			// Increment the count if it exists.
			if (actualVersionRowspanCount.containsKey(entry.actualVersion()))
				actualVersionRowspanCount.replace(entry.actualVersion(), actualVersionRowspanCount.get(entry.actualVersion())+1);
			// If it hasn't been counted, add the first tally.
			else
				actualVersionRowspanCount.put(entry.actualVersion(), 1);

			// Build
			// Increment the count if it exists.
			if (buildRowspanCount.containsKey(entry.declaredBuild()))
				buildRowspanCount.replace(entry.declaredBuild(), buildRowspanCount.get(entry.declaredBuild())+1);
			// If it hasn't been counted, add the first tally.
			else
				buildRowspanCount.put(entry.declaredBuild(), 1);

			// Date (Count actualBuild() and not date() because x.0 GM and x.1 beta can technically be pushed at the same time.)
			// Increment the count if it exists.
			if (dateRowspanCount.containsKey(entry.actualBuild()))
				dateRowspanCount.replace(entry.actualBuild(), dateRowspanCount.get(entry.actualBuild())+1);
			// If it hasn't been counted, add the first tally.
			else
				dateRowspanCount.put(entry.actualBuild(), 1);

			// File URL
			// Increment the count if it exists.
			if (fileRowspanCount.containsKey(entry.url()))
				fileRowspanCount.replace(entry.url(), fileRowspanCount.get(entry.url())+1);
			// If it hasn't been counted, add the first tally.
			else
				fileRowspanCount.put(entry.url(), 1);

			// Marketing version
			// Increment the count if it exists.
			if (marketingVersionRowspanCount.containsKey(entry.marketingVersion()))
				marketingVersionRowspanCount.replace(entry.marketingVersion(), marketingVersionRowspanCount.get(entry.marketingVersion())+1);
			// If it hasn't been counted, add the first tally.
			else
				marketingVersionRowspanCount.put(entry.marketingVersion(), 1);

			// Prerequisite version
			if (prereqRowspanCount.containsKey(entry.declaredBuild())) // Load nested HashMap into variable temporarily, if it exists.
				prereqNestedCount = prereqRowspanCount.get(entry.declaredBuild());

			// Increment the count if it exists.
			if (prereqNestedCount.containsKey(entry.prerequisiteVer()))
				prereqNestedCount.replace(entry.prerequisiteVer(), prereqNestedCount.get(entry.prerequisiteVer())+1);
			// If it hasn't been counted, add the first tally.
			else
				prereqNestedCount.put(entry.prerequisiteVer(), 1);

			prereqRowspanCount.put(entry.declaredBuild(), prereqNestedCount);
		}
	}

	private static void loadFile(final File PLIST_NAME) {
		try {
			root = (NSDictionary)PropertyListParser.parse(PLIST_NAME);
		}
		catch (FileNotFoundException e) {
			System.err.println("ERROR: The file \"" + PLIST_NAME + "\" can't be found.");
			System.exit(2);
		}
		catch (PropertyListFormatException e) {
			System.err.println("ERROR: This isn't an Apple property list.");
			System.exit(6);
		}
		catch (SAXException e) {
			System.err.println("ERROR: This file doesn't have proper XML syntax.");
			System.exit(7);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void main(final String[] args) {
		boolean mwMarkup = false;
		int i = 0;
		String arg = "", plistName = "";

		System.out.println("OTA Catalog Parser v0.3.3");
		System.out.println("https://github.com/Dialexio/OTA-Catalog-Parser\n");

		// Reading arguments (and performing some basic checks).
		while (i < args.length && args[i].startsWith("-")) {
			arg = args[i++];

			if (arg.equals("-b"))
				showBeta = true;

			else if (arg.equals("-d")) {
				device = "";

				if (i < args.length)
					device = args[i++];

				if (!device.matches("((AppleTV|iP(ad|hone|od))|Watch)\\d(\\d)?,\\d")) {
					System.err.println("ERROR: You need to set a device with the \"-d\" argument, e.g. iPhone3,1 or iPad2,7");
					System.exit(1);
				}
			}

			else if (arg.equals("-f")) {
				plistName = "";

				if (i < args.length)
					plistName = args[i++];

				if (plistName.isEmpty()) {
					System.err.println("ERROR: You need to supply a file name.");
					System.exit(2);
				}
			}

			else if (arg.equals("-m")) {
				model = "";

				if (i < args.length)
					model = args[i++];

				if (!model.matches("[JKMNP]\\d(\\d)?(\\d)?[A-Za-z]?AP")) {
					System.err.println("ERROR: You need to specify a model with the \"-m\" argument, e.g. N71AP");
					System.exit(3);
				}
			}

			else if (arg.equals("-max")) {
				maxOSVer = "";

				if (i < args.length)
					maxOSVer = args[i++];

				if (!maxOSVer.matches("\\d\\.\\d(\\.\\d)?(\\d)?")) {
					System.err.println("ERROR: You need to specify a version of iOS if you are using the \"-max\" argument, e.g. 4.3 or 8.0.1");
					System.exit(4);
				}
			}
			else if (arg.equals("-min")) {
				minOSVer = "";

				if (i < args.length)
					minOSVer = args[i++];

				if (!minOSVer.matches("\\d\\.\\d(\\.\\d)?(\\d)?")) {
					System.err.println("ERROR: You need to specify a version of iOS if you are using the \"-min\" argument, e.g. 4.3 or 8.0.1");
					System.exit(5);
				}
			}
			else if (arg.equals("-w"))
				mwMarkup = true;
		}

		// Flag whether or not we need to check the model.
		// Right now, it's just a lazy check for iPhone8,1 or iPhone8,2.
		checkModel = device.matches("iPhone8,(1|2)");

		loadFile(new File(plistName));

		addEntries(root);

		sort();

		if (mwMarkup) {
			countRowspan(ENTRY_LIST);
			printWikiMarkup(ENTRY_LIST);
		}
		else
			printOutput(ENTRY_LIST);
	}

	private static void printOutput(final ArrayList<OTAPackage> ENTRYLIST) {
		for (OTAPackage entry:ENTRYLIST) {
			// Output iOS version and build.
			System.out.print("iOS " + entry.marketingVersion() + " (Build " + entry.actualBuild() + ")");
			if (!entry.actualBuild().equals(entry.declaredBuild()))
				System.out.print(" (listed as " + entry.declaredBuild() + ")");
			System.out.println();

			// Is this a beta?
			if (entry.isBeta())
				System.out.println("This is a beta release.");
			else if (entry.declaredBeta())
				System.out.println("This is marked as a beta release (but is not one).");

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				System.out.println("Requires: Not specified");
			else {
				System.out.print("Requires: ");

				// Version isn't always specified.
				if (entry.prerequisiteVer().equals("N/A"))
					System.out.print("Version not specified");
				else
					System.out.print("iOS " + entry.prerequisiteVer());

				System.out.println(" (Build " + entry.prerequisiteBuild() + ")");
			}

			// Date as extracted from the URL.
			System.out.println("Timestamp: " + entry.date().substring(0, 4) + "/" + entry.date().substring(4, 6) + "/" + entry.date().substring(6));

			// Print out the URL and file size.
			System.out.println("URL: " + entry.url());
			System.out.println("File size: " + entry.size());

			System.out.println();
		}
	}

	private static void printWikiMarkup(final ArrayList<OTAPackage> ENTRYLIST) {
		final Pattern nameRegex = Pattern.compile("[0-9a-f]{40}\\.zip");
		Matcher name;
		String fileName;

		for (OTAPackage entry:ENTRYLIST) {
			fileName = "";

			name = nameRegex.matcher(entry.url());
			while (name.find()) {
				fileName = name.group();
				break;
			}

			// Let us begin!
			System.out.println("|-");

			//Marketing Version for Apple Watch.
			if (device.matches("Watch\\d(\\d)?,\\d") && marketingVersionRowspanCount.containsKey(entry.marketingVersion())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (marketingVersionRowspanCount.get(entry.marketingVersion()).intValue() > 1)
					System.out.print("rowspan=\"" + marketingVersionRowspanCount.get(entry.marketingVersion()) + "\" | ");

				System.out.print(entry.marketingVersion());

				// Give it a beta label (if it is one).
				if (entry.isBeta())
					// Number sign should be replaced by user; we can't keep track of which beta this is.
					System.out.print(" beta #");

				System.out.println();
				// End of iOS version printing.

				//Remove the count since we're done with it.
				marketingVersionRowspanCount.remove(entry.marketingVersion());
			}

			// Output OS version.
			if (actualVersionRowspanCount.containsKey(entry.actualVersion())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (actualVersionRowspanCount.get(entry.actualVersion()).intValue() > 1)
					System.out.print("rowspan=\"" + actualVersionRowspanCount.get(entry.actualVersion()) + "\" | ");

				System.out.print(entry.actualVersion());

				// Give it a beta label (if it is one).
				if (entry.isBeta())
					// Number sign should be replaced by user; we can't keep track of which beta this is.
					System.out.print(" beta #");

				System.out.println();
				// End of iOS version printing.

				// Output a filler for Marketing Version, if this is a 32-bit Apple TV.
				if (device.matches("AppleTV(2,1|3,1|3,2)")) {
					System.out.print("| ");

					// Only give rowspan if there is more than one row with the OS version.
					if (actualVersionRowspanCount.get(entry.actualVersion()).intValue() > 1)
						System.out.print("rowspan=\"" + actualVersionRowspanCount.get(entry.actualVersion()) + "\" | ");

					System.out.println("[MARKETING VERSION]");
				}

				//Remove the count since we're done with it.
				actualVersionRowspanCount.remove(entry.actualVersion());
			}

			// Output build number.
			if (buildRowspanCount.containsKey(entry.declaredBuild())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				// Count declaredBuild() instead of actualBuild() so the entry pointing betas to the final build is treated separately.
				if (buildRowspanCount.get(entry.declaredBuild()).intValue() > 1) {
					System.out.print("rowspan=\"" + buildRowspanCount.get(entry.declaredBuild()) + "\" | ");
					buildRowspanCount.remove(entry.declaredBuild());
				}

				System.out.print(entry.actualBuild());

				// Do we have a fake beta?
				if (entry.declaredBeta() && !entry.isBeta())
					System.out.print("<ref name=\"fakefive\" />");

				System.out.println();
			}

			// Print prerequisites if there are any.
			if (entry.isUniversal()) {
				System.out.print("| colspan=\"2\" {{n/a");

				// Is this "universal" OTA update intended for betas?
				if (entry.declaredBeta() && !entry.isBeta())
					System.out.print("|Beta");

				System.out.println("}}");
			}
			else {
				// Prerequisite version
				if (prereqRowspanCount.containsKey(entry.declaredBuild()) && prereqRowspanCount.get(entry.declaredBuild()).containsKey(entry.prerequisiteVer())) {
					System.out.print("| ");

					// Is there more than one of this prerequisite version tallied?
					if (prereqRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()).intValue() > 1) {
						System.out.print("rowspan=\"" + prereqRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()) + "\" | ");
						prereqRowspanCount.get(entry.declaredBuild()).remove(entry.prerequisiteVer());
					}

					System.out.println(entry.prerequisiteVer());
				}

				// Prerequisite build
				System.out.println("| " + entry.prerequisiteBuild());
			}

			// Date as extracted from the URL. Using the same rowspan count as build.
			// (3.1.1 had two builds released on different dates for iPod touch 3G.)
			if (dateRowspanCount.containsKey(entry.actualBuild())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (dateRowspanCount.get(entry.actualBuild()).intValue() > 1) {
					System.out.print("rowspan=\"" + dateRowspanCount.get(entry.actualBuild()) + "\" | ");
					dateRowspanCount.remove(entry.actualBuild()); //Remove the count since we already used it.
				}

				System.out.println("{{date|" + entry.date().substring(0, 4) + "|" + entry.date().substring(4, 6) + "|" + entry.date().substring(6) + "}}");
			}

			// Print file URL.
			if (fileRowspanCount.containsKey(entry.url())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (fileRowspanCount.get(entry.url()).intValue() > 1) {
					System.out.print("rowspan=\"" + fileRowspanCount.get(entry.url()) + "\" | ");
				}

				System.out.println("[" + entry.url() + " " + fileName + "]");
			}

			//Print file size.
			if (fileRowspanCount.containsKey(entry.url())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (fileRowspanCount.get(entry.url()).intValue() > 1) {
					System.out.print("rowspan=\"" + fileRowspanCount.get(entry.url()) + "\" | ");
					fileRowspanCount.remove(entry.url());
				}

				System.out.println(entry.size());
			}
		}
	}

	private static void sort() {
		Collections.sort(ENTRY_LIST, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).sortingPrerequisiteBuild().compareTo(((OTAPackage)package2).sortingPrerequisiteBuild());
			}
		});
		Collections.sort(ENTRY_LIST, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).sortingBuild().compareTo(((OTAPackage)package2).sortingBuild());
			}
		});
		Collections.sort(ENTRY_LIST, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).marketingVersion().compareTo(((OTAPackage)package2).marketingVersion());
			}
		});
	}
}
