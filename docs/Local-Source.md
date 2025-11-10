Follow the steps below to create local manga.

1. Place correctly structured manga inside the `local` directory located in [Suwayomi's data directory](https://github.com/Suwayomi/Suwayomi-Server/wiki/The-Data-Directory).
1. You can then access the manga in the **Local source** source.

If you add more chapters then you'll have to manually refresh the chapter list.

Supported chapter formats are folder with pictures inside (such as `.jpg`, `.png`, etc.), `ZIP`/`CBZ`, `RAR`/`CBR` and `EPUB`. But expect better performance with directories and `ZIP`/`CBZ`.

**Note:** While Suwayomi does support chapters compressed as **RAR** or **CBR**, note that **RAR** or **CBR** files using the **RAR5** format are not supported yet.

**Note:** If **CBR** or **RAR** files do not work, you may need to extract and re-compress them into one of the supported formats.


## Folder Structure

Suwayomi requires a specific folder structure for local manga to be correctly processed. Local manga will be read from the `local` folder. Each manga must have a `Manga` folder and a `Chapter` folder. Images will then go into the chapter folder. See below for more information on archive files. You can refer to the following example:


<div class="side-by-side">
	<ul class="file-tree">
		<li>
			local/
			<ul>
				<li>
					<span class="ft-icon ft-folder">Manga title/</span>
					<ul>
						<li>
							<span class="ft-icon ft-folder">Chapter 01 - Prologue/</span>
							<ul>
								<li><span class="ft-icon ft-image">page01.png</span></li>
								<li><span class="ft-icon ft-image">page02.png</span></li>
								<li><span class="ft-icon ft-image">page03.png</span></li>
								<li><span class="ft-icon ft-image">...</span></li>
							</ul>
						</li>
						<li>
							<span class="ft-icon ft-folder">Chapter 02 - A hero's journey/</span>
							<ul>
								<li><span class="ft-icon ft-image">01.jpg</span></li>
								<li><span class="ft-icon ft-image">02.jpg</span></li>
								<li><span class="ft-icon ft-image">03.jpg</span></li>
								<li><span class="ft-icon ft-image">...</span></li>
							</ul>
						</li>
						<li><span class="ft-icon ft-image">cover.jpg</span></li>
					</ul>
				</li>
				<li>
					<span class="ft-icon ft-folder">Other Manga title/</span>
					<ul>
						<li><span class="ft-icon ft-image">cover.jpg</span></li>
						<li>
							<span class="ft-icon ft-folder">ch001/</span>
							<ul>
								<li><span class="ft-icon ft-image">01 - cover.jpg</span></li>
								<li><span class="ft-icon ft-image">02 - first page.jpeg</span></li>
								<li><span class="ft-icon ft-image">03 - second.jpg</span></li>
								<li><span class="ft-icon ft-image">...</span></li>
							</ul>
						</li>
						<li>
							<span class="ft-icon ft-folder">ch002/</span>
							<ul>
								<li><span class="ft-icon ft-image">001.jpg</span></li>
								<li><span class="ft-icon ft-image">002.jpg</span></li>
								<li><span class="ft-icon ft-image">003.jpg</span></li>
								<li><span class="ft-icon ft-image">...</span></li>
							</ul>
						</li>
					</ul>
				</li>
				<li>...</li>
			</ul>
		</li>
	</ul>
</div>

**Note:** Chapter and page names should be ordered alphanumerically, using zero padded name prefixes is usually the best option.

The path to the folder with images must contain both the manga title and the chapter name (as seen above).

## Archive Files
Archive files such as `ZIP`/`CBZ` are supported but the folder structure inside is not. Any folders inside the archive file are ignored. You must place the archive inside the `Manga` folder where the name will become the `Chapter` title. All images inside the archive regardless of folder structure will become pages for that chapter.

<ul class="file-tree">
		<li>
			local/
			<ul>
				<li>
			<span class="ft-icon ft-folder">Manga title/</span>
			<ul>
				<li>
					<span class="ft-icon ft-zip">ch1.zip</span>
				</li>
					<li>
					<span class="ft-icon ft-zip">ch2.zip</span>
					</li>
						<span class="ft-icon ft-image">cover.jpg</span>
					</ul>
				</li>
				<li>...</li>
			</ul>
		</li>
</ul>


## Advanced

### Editing local manga details

It is possible to add details to local manga. Like manga from other catalogs, you add information about the manga such as the author, artist, description, and genre tags.

To import details along with your local manga, you have to create a json file. It can be named anything but it must be placed within the **Manga** folder. A standard file name is `details.json`. This file will contain the extended details about the manga in the `JSON` format. You can see the example below on how to build the file. Once the file is there, the app should load the data when you first open the manga, or you can pull down to refresh the details.

You can copy the following example and edit the details as needed:
``` json
{
	"title": "Example Title",
	"author": "Example Author",
	"artist": "Example Artist",
	"description": "Example Description",
	"genre": ["genre 1", "genre 2", "etc"],
	"status": "0",
	"_status values": ["0 = Unknown", "1 = Ongoing", "2 = Completed", "3 = Licensed"]
}
```

### Using a custom cover image

It is also possible to use a custom image as a cover for each local manga.

To do this, you only need to place the image file, that needs to be named
`cover.jpg`, in the root of the manga folder. The app will then use your
custom image in the local source listing.
