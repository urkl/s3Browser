package com.example.application.views.mojzabojnik;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDescriptor;
import com.drew.metadata.exif.GpsDirectory;
import com.example.application.FileSystemItem;
import com.example.application.services.S3Service;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.imaging.ImageFormats.*;

@Route("")
public class MainView extends HorizontalLayout {

    private final S3Service s3Service;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private TreeGrid<FileSystemItem> treeGrid;
    private FileSystemItem selectedFolder;
    private Image previewImage;
    private HierarchicalDataProvider<FileSystemItem, Void> dataProvider;
    private Anchor mapLink;
    @Autowired
    public MainView(S3Service s3Service) {
        this.s3Service = s3Service;

        setSizeFull(); // Set the size of the main layout to full
        setPadding(false);
        setSpacing(false);

        VerticalLayout leftLayout = new VerticalLayout();
        leftLayout.setSizeFull();
        leftLayout.setPadding(false);
        leftLayout.setSpacing(false);

        // Create folder components
        TextField folderNameField = new TextField("Folder Name");
        Button createFolderButton = new Button("Create Folder", VaadinIcon.FOLDER_ADD.create());
        createFolderButton.addClickListener(event -> {
            String folderName = folderNameField.getValue();
            if (!folderName.isEmpty()) {
                String key = (selectedFolder != null && selectedFolder.isFolder()) ? selectedFolder.getName() + folderName + "/" : folderName + "/";
                s3Service.createFolder(key);
                folderNameField.clear();
                Notification.show("Folder created successfully", 3000, Notification.Position.MIDDLE);
                if (selectedFolder != null) {
                    treeGrid.getDataProvider().refreshItem(selectedFolder, true);
                    if (!treeGrid.isExpanded(selectedFolder)) {
                        treeGrid.expand(selectedFolder);
                    }
                } else {
                    treeGrid.getDataProvider().refreshAll();
                }
            } else {
                Notification.show("Folder name cannot be empty", 3000, Notification.Position.MIDDLE);
            }
        });

        HorizontalLayout folderCreationLayout = new HorizontalLayout(folderNameField, createFolderButton);

        // Search component
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search files...");
        Button searchButton = new Button("Search", VaadinIcon.SEARCH.create());
        searchButton.addClickListener(event -> {
            String query = searchField.getValue();
            if (!query.isEmpty()) {
                List<FileSystemItem> searchResults = s3Service.searchFiles(query);
                treeGrid.setItems(searchResults);
            } else {
                Notification.show("Search query cannot be empty", 3000, Notification.Position.MIDDLE);
            }
        });

        HorizontalLayout searchLayout = new HorizontalLayout(searchField, searchButton);

        // Upload component
        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setWidthFull();
        upload.setDropLabel(new Span("Upload Files Here"));

        // Set unlimited file size
        upload.setMaxFileSize(-1);
        upload.setAcceptedFileTypes(); // No restrictions on file types
        upload.setMaxFiles(Integer.MAX_VALUE); // Allow uploading multiple files

        upload.addSucceededListener(event -> {
            String fileName = event.getFileName();
            InputStream inputStream = buffer.getInputStream(fileName);
            String key = (selectedFolder != null && selectedFolder.isFolder()) ? selectedFolder.getName() + fileName : fileName;
            try {
                s3Service.uploadFile(key, inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Notification.show("File uploaded successfully", 3000, Notification.Position.MIDDLE);

            if (selectedFolder != null) {
                treeGrid.getDataProvider().refreshItem(selectedFolder, true);
                if (!treeGrid.isExpanded(selectedFolder)) {
                    treeGrid.expand(selectedFolder);
                }
            } else {
                treeGrid.getDataProvider().refreshAll();
            }

            // Clear the upload component after all files are uploaded
            upload.clearFileList();
        });

        // TreeGrid component
        treeGrid = new TreeGrid<>();
        treeGrid.setSizeFull(); // Set the size of the TreeGrid to full
        treeGrid.addComponentHierarchyColumn(this::renderFileSystemItem).setHeader("Name");

        dataProvider = new AbstractBackEndHierarchicalDataProvider<FileSystemItem, Void>() {
            @Override
            public int getChildCount(HierarchicalQuery<FileSystemItem, Void> query) {
                FileSystemItem item = query.getParent();
                if (item == null) {
                    return s3Service.listS3Objects("").size();
                } else {
                    return s3Service.listS3Objects(item.getName()).size();
                }
            }

            @Override
            public boolean hasChildren(FileSystemItem item) {
                return item.isFolder();
            }



            @Override
            protected Stream<FileSystemItem> fetchChildrenFromBackEnd(HierarchicalQuery<FileSystemItem, Void> query) {
                FileSystemItem item = query.getParent();
                if (item == null) {
                    return s3Service.listS3Objects("").stream();
                } else {
                    return s3Service.listS3Objects(item.getName()).stream();
                }
            }
        };

        treeGrid.setDataProvider(dataProvider);
        treeGrid.addSelectionListener(event -> event.getFirstSelectedItem().ifPresent(item -> selectedFolder = item));

        leftLayout.add(searchLayout, folderCreationLayout, upload, treeGrid);
        leftLayout.expand(treeGrid); // Ensure TreeGrid takes all remaining space

        // Image preview
        previewImage = new Image();
        previewImage.setWidth("100%");
        previewImage.setHeight("100%");
        previewImage.setMaxWidth("500px"); // Set max width for the image
        previewImage.setMaxHeight("500px"); // Set max height for the image
        previewImage.setVisible(false);

        add(leftLayout, previewImage);
        setFlexGrow(1, leftLayout);
        setFlexGrow(1, previewImage);

        VerticalLayout previewLayout = new VerticalLayout();
        previewImage = new Image();
        previewImage.setWidth("100%");
        previewImage.setHeight("100%");
        previewImage.setMaxWidth("500px");
        previewImage.setMaxHeight("500px");
        previewImage.setVisible(false);


        exifDataLabel.getStyle().set("font-family", "Arial, sans-serif");
        exifDataLabel.getStyle().set("font-size", "14px");

        mapLink = new Anchor();
        mapLink.setText("Prikaži na Google Zemljevidih");
        mapLink.setVisible(false);

        previewLayout.add(previewImage, exifDataLabel, mapLink);

        add(leftLayout, previewLayout);
        setFlexGrow(1, leftLayout);
        setFlexGrow(1, previewLayout);
    }

    private Component renderFileSystemItem(FileSystemItem item) {
        Icon icon = getFileIcon(item);
        String name = getFileName(item);

        HorizontalLayout layout = new HorizontalLayout(icon, new Span(name));
        layout.setAlignItems(Alignment.CENTER);
        layout.addClickListener(e -> {
            treeGrid.getSelectionModel().select(item);
            if (isImageFile(item.getName())) {
                showImagePreview(item);
            } else {
                previewImage.setVisible(false);
            }
        });

        return layout;
    }

    private Icon getFileIcon(FileSystemItem item) {
        if (item.isFolder()) {
            return VaadinIcon.FOLDER.create();
        }

        String fileName = item.getName().toLowerCase();
        if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif")) {
            return VaadinIcon.PICTURE.create();
        } else if (fileName.endsWith(".pdf")) {
            return VaadinIcon.FILE_PRESENTATION.create();
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            return VaadinIcon.FILE_TEXT.create();
        } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            return VaadinIcon.FILE_TABLE.create();
        } else if (fileName.endsWith(".txt")) {
            return VaadinIcon.FILE_CODE.create();
        } else {
            return VaadinIcon.FILE.create();
        }
    }

    private String getFileName(FileSystemItem item) {
        String name = item.getName();
        if (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name;
    }
    private NativeLabel exifDataLabel=new NativeLabel();
    private boolean isImageFile(String fileName) {
        fileName = fileName.toLowerCase();
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif");
    }

    private void appendExifData(StringBuilder exifData, String label, Map<TagInfo, String> tagMap, TagInfo tagInfo) {
        String value = tagMap.get(tagInfo);
        if (value != null) {
            exifData.append(label).append(": ").append(value).append("\n");
        }
    }

    private void appendExifData(StringBuilder exifData, String label, double value) {
        exifData.append(label).append(": ").append(value).append("\n");
    }

    private double convertToDegrees(String dmsString, String ref) {
        String[] dmsArray = dmsString.split(",");
        double degrees = Double.parseDouble(dmsArray[0].trim());
        double minutes = Double.parseDouble(dmsArray[1].trim());
        double seconds = Double.parseDouble(dmsArray[2].trim());
        double result = degrees + (minutes / 60.0) + (seconds / 3600.0);
        if (ref.equals("S") || ref.equals("W")) {
            result = -result;
        }
        return result;
    }

    private void showImagePreview(FileSystemItem item) {
        StreamResource resource = new StreamResource(getFileName(item), () -> s3Service.getFileStream(item.getName()));
        previewImage.setSrc(resource);
        previewImage.setVisible(true);

        try (InputStream inputStream = s3Service.getFileStream(item.getName())) {
            String exifData = readExifData(inputStream,getFileName(item));
            exifDataLabel.setText(exifData);
            exifDataLabel.setVisible(true);
        } catch (IOException e) {
            e.printStackTrace();
            exifDataLabel.setText("Unable to read EXIF data.");
            exifDataLabel.setVisible(true);
        }
    }
    private void appendExifData(Div exifData, String label, String value) {

        Div div = new Div();

        if (value != null) {
            div.setText(label + ": " + value);
            exifData.add(div);
        } else {
            div.setText(label + ": " + ": not available");
            exifData.add(div);
        }
    }

    private String createGpsLink(double latitude, double longitude) {
        return "https://www.google.com/maps?q=" + latitude + "," + longitude;
    }
        private String readExifData(InputStream inputStream,String fileName) throws IOException {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            Div exifData = new Div();

            ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            ExifIFD0Directory ifd0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

            if (exifDirectory != null) {
                appendExifData(exifData, "Model kamere", ifd0Directory.getString(ExifIFD0Directory.TAG_MODEL));
                appendExifData(exifData, "Datum/Čas", exifDirectory.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));
                appendExifData(exifData, "Širina slike", exifDirectory.getString(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH));
                appendExifData(exifData, "Višina slike", exifDirectory.getString(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT));
                appendExifData(exifData, "ISO", exifDirectory.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
                appendExifData(exifData, "Zaslonka", exifDirectory.getString(ExifSubIFDDirectory.TAG_FNUMBER));
                appendExifData(exifData, "Čas osvetlitve", exifDirectory.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME));
                appendExifData(exifData, "Goriščna razdalja", exifDirectory.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
            } else {
                exifData.setText("No EXIF data found.");
            }

            add(exifData);

            // If GPS data is available, add a button to show the location
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                GpsDescriptor gpsDescriptor = new GpsDescriptor(gpsDirectory);
                double[] latLon = getDecimalCoordinates(gpsDirectory);

                if (latLon != null) {
                    String gpsLink = createGpsLink(latLon[0], latLon[1]);

                    Button gpsButton = new Button("Show GPS Location", event -> {
                        getUI().ifPresent(ui -> ui.getPage().executeJs("window.open('" + gpsLink + "', '_blank')"));
                    });

                    add(gpsButton);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Div errorDiv = new Div();
            errorDiv.setText("Error reading EXIF data");
            add(errorDiv);
        } finally {
            inputStream.close();
        }

        return "aaaaaaaaaaaaaa";
    }
    private double[] getDecimalCoordinates(GpsDirectory gpsDirectory) {
        if (gpsDirectory == null) {
            return null;
        }

        try {
            double latitude = convertToDegrees(gpsDirectory.getRationalArray(GpsDirectory.TAG_LATITUDE),
                    gpsDirectory.getString(GpsDirectory.TAG_LATITUDE_REF));
            double longitude = convertToDegrees(gpsDirectory.getRationalArray(GpsDirectory.TAG_LONGITUDE),
                    gpsDirectory.getString(GpsDirectory.TAG_LONGITUDE_REF));

            return new double[]{latitude, longitude};
        } catch (Exception e) {
            return null;
        }
    }

    private double convertToDegrees(com.drew.lang.Rational[] coordinate, String ref) {
        if (coordinate == null || coordinate.length != 3) {
            return 0.0;
        }

        double degrees = coordinate[0].doubleValue();
        double minutes = coordinate[1].doubleValue();
        double seconds = coordinate[2].doubleValue();

        double result = degrees + (minutes / 60.0) + (seconds / 3600.0);
        if ("S".equals(ref) || "W".equals(ref)) {
            return -result;
        }
        return result;
    }
}