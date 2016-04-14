/*
 * OTA Catalog Parser
 * Copyright (c) 2016 Dialexio
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
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.*;

class OTAPackage {
	private final int SIZE;
	private final NSDictionary ENTRY;
	private final String DOC_ID, URL,
		REGEX_BETA = "(\\d)?\\d[A-Z][4-6]\\d{3}[a-z]?";
	private Matcher match;
	private NSObject[] supportedDeviceModels = null, supportedDevices;
	private String date;

	public OTAPackage(NSDictionary otaEntry) {
		ENTRY = otaEntry;
		otaEntry = null;

		DOC_ID = ENTRY.containsKey("SUDocumentationID") ? ENTRY.get("SUDocumentationID").toString() : "0Seed";
		supportedDevices = ((NSArray)ENTRY.objectForKey("SupportedDevices")).getArray();

		final Pattern TIMESTAMP_REGEX = Pattern.compile("\\d{4}(\\-|\\.)\\d{7}(\\d)?");

		// Retrieve the list of supported models... if it exists.
		if (ENTRY.containsKey("SupportedDeviceModels"))
			supportedDeviceModels = ((NSArray)ENTRY.objectForKey("SupportedDeviceModels")).getArray();

		// Obtaining the size and URL.
		// First, we need to make sure we don't get info for a dummy update.
		if (ENTRY.containsKey("RealUpdateAttributes")) {
			NSDictionary REAL_UPDATE_ATTRS = (NSDictionary)ENTRY.get("RealUpdateAttributes");

			SIZE = ((NSNumber)REAL_UPDATE_ATTRS.get("RealUpdateDownloadSize")).intValue();
			URL = REAL_UPDATE_ATTRS.get("RealUpdateURL").toString();
			REAL_UPDATE_ATTRS = null;
		}

		else {
			SIZE = ((NSNumber)ENTRY.get("_DownloadSize")).intValue();
			URL = ((NSString)ENTRY.get("__BaseURL")).getContent() + ((NSString)ENTRY.get("__RelativePath")).getContent();
		}

		// Extract the date from the URL.
		// This is not 100% accurate information, especially with releases like 8.0, 8.1, 8.2, etc., but better than nothing.
		match = TIMESTAMP_REGEX.matcher(URL);
		date = (match.find()) ? match.group().substring(5) : "Not Available";

		if (date.length() == 7)
			date = date.substring(0, 6) + '0' + date.substring(6);
	}

	/**
	 * Returns the actual build number of the OTA entry.
	 * Sometimes, Apple likes to add a large number to the end.
	 * 
	 * @return The build number that iOS will report in Settings.
     **/
	public String actualBuild() {
		// If it the build number looks like a beta...
		// And it's labeled as a beta...
		// But it's not a beta... We need the actual build number.
		if (this.declaredBuild().matches(REGEX_BETA) && this.isDeclaredBeta() && this.betaType() == 0) {
			int letterPos, numPos;

			for (letterPos = 1; letterPos < this.declaredBuild().length(); letterPos++) {
				if (Character.isUpperCase(this.declaredBuild().charAt(letterPos))) {
					letterPos++;
					break;
				}
			}

		    numPos = letterPos + 1;
			if (this.declaredBuild().charAt(numPos) == '0')
				numPos++;

			return this.declaredBuild().substring(0, letterPos) + this.declaredBuild().substring(numPos);
		}
		
		else
			return this.declaredBuild();
	}

	/**
	 * Returns the beta number of this entry.
	 * If a beta number cannot be determined, returns 0.
	 * 
	 * @return Whatever number beta this is, as an int.
     **/
	public int betaNumber() {
		if (this.betaType() > 0 && !DOC_ID.equals("PreRelease")) {
			final char digit = DOC_ID.charAt(DOC_ID.length()-1);

			return (Character.isDigit(digit)) ? Integer.parseInt(digit + "") : 1;
		}

		else
			return 0;
	}

	/**
	 * Checks if the release is a developer beta, a public beta, or not a beta.
	 * 
	 * @return An integer value of 0 (not a beta), 1 (public beta), 2 (developer beta), 3 (carrier beta), or 4 (internal build).
     **/
	public int betaType() {
		// Just check ReleaseType and return values based on it.
		// We do need to dig deeper if it's "Beta" though.
		if (ENTRY.containsKey("ReleaseType")) {
			switch (ENTRY.get("ReleaseType").toString()) {
				case "Beta":
					break;

				case "Carrier":
					return 3;

				case "Internal":
					return 4;

				default:
					System.err.println("Unknown ReleaseType: "+ ENTRY.get("ReleaseType").toString());
					return -1;
			}
		}

		else
			return 0;


		// Further investigations for ReleaseType = "Beta"

		if (DOC_ID.equals("PreRelease"))
			return 2;

		// Hack to force large OTA updates to return 0.
		// I have never seen a beta OTA update exceed this size.
		else if (SIZE > 550000000)
			return 0;

		else {
			if (DOC_ID.contains("Public"))
				return 1;

			else if (DOC_ID.contains("Beta") || DOC_ID.contains("Seed"))
				return 2;

			else 
				return 0;
		}
	}

	/**
	 * Returns the timestamp found in the URL.
	 * Note that this is not always accurate;
	 * it may be off by a day, or even a week.
	 * 
	 * @return The timestamp found in the URL.
     **/
	public String date() {
		return date;
	}

	/**
	 * Returns part of the timestamp found in the URL.
	 * 
	 * @param dmy	Specifies if you are looking for the <b>d</b>ay, <b>m</b>onth, or <b>y</b>ear.
	 * If neither 'd', 'm', or 'y' is specified, it will return the entire timestamp.
	 * 
	 * @return The day, month, or year found in the URL.
     **/
	public String date(final char dmy) {
		switch (dmy) {
			// Day
			case 'd':
				return date.substring(6);

			// Month
			case 'm':
				return date.substring(4, 6);

			// Year
			case 'y':
				return date.substring(0, 4);

			default:
				return date;
		}
	}

	/**
	 * @return The build as listed in the OTA update catalog.
     **/
	public String declaredBuild() {
		return ENTRY.get("Build").toString();
	}

	/**
	 * Checks if Apple marked the OTA package with a release type.
	 * This function only checks for the existence of such a key,
	 * and not its value.
	 * 
	 * @return A boolean value of whether Apple claims this is a beta release (true) or not (false).
     **/
	public boolean isDeclaredBeta() {
		return ENTRY.containsKey("ReleaseType");
	}

	/**
	 * Checks if the release is a large, "one size fits all" package.
	 * 
	 * @return A boolean value of whether this release is used to cover all scenarios (true) or not (false).
     **/
	public boolean isUniversal() {
		return (this.prerequisiteVer().equals("N/A") && this.prerequisiteBuild().equals("N/A"));
	}

	/**
	 * Returns the value of "MarketingVersion" if present.
	 * "MarketingVersion" is used in some entries to display
	 * a false version number (e.g. watchOS 2).
	 * 
	 * @return A String value of the "MarketingVersion" key, or "OSVersion" key.
     **/
	public String marketingVersion() {
		if (ENTRY.containsKey("MarketingVersion")) {
			if (!ENTRY.get("MarketingVersion").toString().contains("."))
				return ENTRY.get("MarketingVersion").toString() + ".0";
			else
				return ENTRY.get("MarketingVersion").toString();
		}

		else
			return ENTRY.get("OSVersion").toString();
	}

	/**
	 * @return The "OSVersion" key, as a String.
     **/
	public String osVersion() {
		return ENTRY.get("OSVersion").toString();
	}

	/**
	 * "PrerequisiteBuild" states the specific build that
	 * the OTA package is intended for.
	 *
	 * @return The "PrerequisiteBuild" key, as a String.
     **/
	public String prerequisiteBuild() {
		return (ENTRY.containsKey("PrerequisiteBuild")) ? ENTRY.get("PrerequisiteBuild").toString() : "N/A";
	}

	/**
	 * "PrerequisiteVersion" states the specific version that
	 * the OTA package is intended for.
	 *
	 * @return The "PrerequisiteVersion" key, as a String.
     **/
	public String prerequisiteVer() {
		if (ENTRY.containsKey("PrerequisiteOSVersion"))
			return ENTRY.get("PrerequisiteOSVersion").toString();

		else {
			switch (this.prerequisiteBuild()) {
			case "10A405":
				return "6.0";

			case "10B141":
				return "6.1";

			default:
				return "N/A";
			}
		}
	}

	/**
	 * @return The package's file size, as a String. It is formatted with commas.
     **/
	public String size() {
		return NumberFormat.getNumberInstance(Locale.US).format(SIZE);
	}

	/**
	 * "sortingBuild()" is the regular build number, with additional zeroes
	 * in the beginning to make sure it's arranged on top.
	 *
	 * @return A String with the same value as OTAPackage.build(),
	 * but with a number of zeroes in front so the program arranges it above
	 * newer entries.
     **/
	public String sortingBuild() {
		int letterPos;
		String sortBuild = this.declaredBuild();

		// Make 9A### appear before 10A###.
		if (Character.isLetter(sortBuild.charAt(1)))
			sortBuild = '0' + sortBuild;

		// If the build number is false, replace everything after the letter with "0000."
		// This will cause betas to appear first.
		if (this.actualBuild().equals(this.declaredBuild()) == false) {
			for (letterPos = 1; letterPos < sortBuild.length(); letterPos++) {
				if (Character.isUpperCase(sortBuild.charAt(letterPos))) {
					letterPos++;
					break;
				}
			}

			sortBuild = sortBuild.substring(0, letterPos) + "0000";
		}

		return sortBuild;
	}

	/**
	 * "sortingMarketingVersion()" is the marketing version,
	 * with additional zeroes in the beginning to make sure
	 * it's arranged on top.
	 *
	 * @return A String with the same value as OTAPackage.marketingVersion(),
	 * but with a number of zeroes in front so the program arranges it above
	 * newer entries.
     **/
	public String sortingMarketingVersion() {
		return (this.marketingVersion().charAt(1) == '.') ? '0' + this.marketingVersion() : this.marketingVersion();
	}

	/**
	 * "sortingPrerequisiteBuild()" is the prerequisite build,
	 * with additional zeroes in the beginning to make sure
	 * it's arranged on top.
	 *
	 * @return A String with the same value as OTAPackage.prerequisiteBuild(),
	 * but with a number of zeroes in front so the program arranges it above
	 * newer entries.
     **/
	public String sortingPrerequisiteBuild() {
		if (this.isUniversal())
			return "0000000000";

		else {
			if (Character.isLetter(this.prerequisiteBuild().charAt(1)))
				return '0' + this.prerequisiteBuild();

			else
				return this.prerequisiteBuild();
		}
	}

	public NSObject[] supportedDeviceModels() {
		return supportedDeviceModels;
	}

	public NSObject[] supportedDevices() {
		return supportedDevices;
	}

	/**
	 * @return The package's URL, as a String.
     **/
	public String url() {
		return URL;
	}
}
