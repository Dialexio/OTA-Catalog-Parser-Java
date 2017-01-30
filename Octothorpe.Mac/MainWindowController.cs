﻿/*
 * Copyright (c) 2017 Dialexio
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
using AppKit;
using Foundation;
using System;
using System.IO;

namespace Octothorpe.Mac
{
	public partial class MainWindowController : NSWindowController
	{
		static bool DisplayWikiMarkup = true;
		static NSAlert alert;
		static Parser parser;

		public MainWindowController(IntPtr handle) : base(handle)
		{
		}

		[Export("initWithCoder:")]
		public MainWindowController(NSCoder coder) : base(coder)
		{
		}

		public MainWindowController() : base("MainWindow")
		{
		}

		public override void AwakeFromNib()
		{
			base.AwakeFromNib();
		}

		public new MainWindow Window
		{
			get { return (MainWindow)base.Window; }
		}

        partial void ChangeOutputFormat(NSButton sender)
        {
            DisplayWikiMarkup = (sender.Title == "The iPhone Wiki markup");
        }

        partial void ParsingSTART(NSButton sender)
        {
            try
            {
                alert = null;
                parser = new Parser();

                parser.Device = NSTextFieldDevice.StringValue;
                parser.Model = NSTextFieldModel.StringValue;
                parser.WikiMarkup = DisplayWikiMarkup;

                parser.ShowBeta = (((NSButton)sender).State == NSCellStateValue.On);

                // Set maximum version if one was specified
                if (String.IsNullOrEmpty(NSTextFieldMax.StringValue) == false)
                    parser.Maximum = new Version(NSTextFieldMax.StringValue);

                // Set maximum version if one was specified
                if (String.IsNullOrEmpty(NSTextFieldMin.StringValue) == false)
                    parser.Minimum = new Version(NSTextFieldMin.StringValue);

                switch (FileSelection.SelectedItem.Title)
                {
                    case "Custom URL...":
                    case "Custom URL…":
                    case "Custom URL":
                        parser.Plist = NSTextFieldURL.StringValue;
                        break;

                    case "iOS (Public)":
                        parser.Plist = "http://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        break;

                    case "tvOS (Public)":
                        parser.Plist = "http://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        break;

                    case "watchOS (Public)":
                        parser.Plist = "http://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        break;

                    default:
                        NSOpenPanel FilePrompt = NSOpenPanel.OpenPanel;
                        FilePrompt.AllowedFileTypes = new string[] { "xml", "plist" };
                        FilePrompt.AllowsMultipleSelection = false;
                        FilePrompt.CanChooseFiles = true;
                        FilePrompt.CanChooseDirectories = false;

                        if (FilePrompt.RunModal() == 1)
                            parser.Plist = FilePrompt.Url.Path;

                        else
                            throw new ArgumentException("nofile");
                        break;
                }

                NSTextViewOutput.Value = parser.ParsePlist();
                parser = null;
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "device":
                        alert = new NSAlert()
                        {
                            MessageText = "Device Not Specified",
                            InformativeText = "You did not specify a device to search for (e.g. iPhone8,1)."
                        };
                        break;

                    case "model":
                        alert = new NSAlert()
                        {
                            MessageText = "Model Not Specified",
                            InformativeText = "This device requires you to specify a model number. For example, N71AP is a model number for the iPhone 6S."
                        };
                        break;

                    case "nofile":
                        alert = new NSAlert()
                        {
                            MessageText = "File Not Specified",
                            InformativeText = "You must select a PLIST file (.plist or .xml) to load."
                        };
                        break;

                    case "notmesu":
                        alert = new NSAlert()
                        {
                            MessageText = "Incorrect URL",
                            InformativeText = "The URL supplied should belong to mesu.apple.com."
                        };
                        break;

                    default:
                        alert = new NSAlert()
                        {
                            MessageText = "Argument Error",
                            InformativeText = "You appear to be missing a required field."
                        };
                        break;
                }

                alert.RunModal();
            }

            catch (FileNotFoundException)
            {
                alert = new NSAlert()
                {
                    MessageText = "File Not Found",
                    InformativeText = "The program was unable to load the specified file."
                };
            }

            // No file selected.
            catch (NullReferenceException)
            {
                alert = new NSAlert()
                {
                    MessageText = "No Catalog Selected",
                    InformativeText = "You need to select an OTA catalog to search through."
                };
                alert.RunModal();
            }

            catch (Exception objection)
            {
                alert = new NSAlert()
                {
                    MessageText = objection.Message,
                    InformativeText = objection.StackTrace
                };
                alert.RunModal();
            }
        }

        partial void SourceChanged(NSPopUpButton sender)
		{
			switch (sender.SelectedItem.Title)
			{
				case "Custom URL...":
				case "Custom URL…":
				case "Custom URL":
					NSBoxURL.Hidden = false;
					break;

				default:
					NSBoxURL.Hidden = true;
					break;
			}
		}

		partial void ToggleModelField(NSTextField sender)
		{
            switch (sender.StringValue)
            {
                case "iPhone8,1":
                case "iPhone8,2":
                case "iPhone8,4":
                    NSBoxModel.Hidden = false;
                    break;

                default:
                    NSBoxModel.Hidden = true;
                    break;
            }
		}
	}
}