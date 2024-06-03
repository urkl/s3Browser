package com.example.application.views.mojzabojnik;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDescriptor;
import com.drew.metadata.exif.GpsDirectory;
import com.example.application.FileSystemItem;
import com.example.application.services.ExifData;
import com.example.application.services.S3Service;
import com.flowingcode.vaadin.addons.gridhelpers.GridHelper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@AnonymousAllowed
@Route("")
public class MainView extends HorizontalLayout {

    private final S3Service s3Service;
    private final Anchor mapLink;
    private final VerticalLayout exifContainer = new VerticalLayout();
    String searchQuery;
    private TreeGrid<FileSystemItem> treeGrid;
    private FileSystemItem selectedFolder;
    private Image previewImage;
    private HierarchicalDataProvider<FileSystemItem, Void> dataProvider = null;
    private Html previewVideo;

    @Autowired
    public MainView(S3Service s3Service) {
        this.s3Service = s3Service;

        setSizeFull(); // Set the size of the main layout to full
        setPadding(false);
        setSpacing(false);

        VerticalLayout leftLayout = new VerticalLayout();
        leftLayout.setSizeFull();
        leftLayout.setPadding(true);
        leftLayout.setSpacing(true);

        // Create folder components
        TextField folderNameField = new TextField();
        folderNameField.setPlaceholder("Ime mape");
        Button createFolderButton = new Button("Ustvari mapo", VaadinIcon.FOLDER_ADD.create());
        createFolderButton.addClickListener(event -> {
            String folderName = folderNameField.getValue();
            if (!folderName.isEmpty()) {
                String key = (selectedFolder != null && selectedFolder.isFolder()) ? selectedFolder.getName() + folderName + "/" : folderName + "/";
                s3Service.createFolder(key);
                folderNameField.clear();
                Notification.show("Mapa ustvarjena", 3000, Notification.Position.MIDDLE);
                if (selectedFolder != null) {
                    treeGrid.getDataProvider().refreshItem(selectedFolder, true);
                    if (!treeGrid.isExpanded(selectedFolder)) {
                        treeGrid.expand(selectedFolder);
                    }
                } else {
                    treeGrid.getDataProvider().refreshAll();
                }
            } else {
                Notification.show("Ime mape ne more biti prazno", 3000, Notification.Position.MIDDLE);
            }
        });

        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        TextField searchField = new TextField();
        searchField.setPlaceholder("Išči datoteke...");
        searchField.addValueChangeListener(event -> {
            searchQuery = event.getValue();
            dataProvider.refreshAll();
        });


        // Upload component
        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setWidthFull();
        upload.setDropLabel(new Span("Naloži datoteke..."));

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

        GridHelper.setArrowSelectionEnabled(treeGrid, true);

        treeGrid.setHeightFull();
        // treeGrid.setSizeFull(); // Set the size of the TreeGrid to full
        var nameColum = treeGrid.addComponentHierarchyColumn(this::renderFileSystemItem).setHeader("Datoteke");


        HeaderRow headerRow = treeGrid.appendHeaderRow();

        headerRow.getCell(nameColum).setComponent(
                searchField);

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
                    return s3Service.listS3Objects(searchQuery).stream();
                } else {
                    return s3Service.listS3Objects(item.getName()).stream();
                }
            }
        };

        treeGrid.setDataProvider(dataProvider);
        treeGrid.addSelectionListener(event -> event.getFirstSelectedItem().ifPresent(item -> selectedFolder = item));

        treeGrid.addSelectionListener(event -> {
            exifContainer.removeAll();
            event.getFirstSelectedItem().ifPresent(item -> {

                if (isImageFile(item.getName())) {
                    showImagePreview(item);
                } else if (isVideoFile(item.getName())) {
                    showVideoPreview(item);
                } else {
                    previewImage.setVisible(false);
                    previewVideo.setVisible(false);
                }
            });
        });


        Button deleteFolderButton = new Button("Izbriši mapo", VaadinIcon.TRASH.create());
        deleteFolderButton.addClickListener(event -> {
            if (selectedFolder != null && selectedFolder.isFolder()) {
                s3Service.deleteFolder(selectedFolder.getName());
                Notification.show("Folder deleted successfully", 3000, Notification.Position.MIDDLE);
                treeGrid.getDataProvider().refreshAll();
            } else {
                Notification.show("Please select a folder to delete", 3000, Notification.Position.MIDDLE);
            }
        });


        toolbar.add(folderNameField, createFolderButton, deleteFolderButton, upload);

        leftLayout.add(toolbar, treeGrid);
        leftLayout.expand(treeGrid); // Ensure TreeGrid takes all remaining space

        // Image preview


//        add(leftLayout, previewImage);
//        setFlexGrow(1, leftLayout);
//        setFlexGrow(1, previewImage);

        VerticalLayout previewLayout = new VerticalLayout();
        previewImage = new Image();
        //previewImage.setWidth("100%");
        //previewImage.setHeight("100%");
        //     previewImage.setMaxWidth("500px");
        previewImage.setMaxHeight("500px");
        previewImage.setVisible(false);


//        exifDataLabel.getStyle().set("font-family", "Arial, sans-serif");
//        exifDataLabel.getStyle().set("font-size", "14px");

        mapLink = new Anchor();
        mapLink.setText("Prikaži na Google Zemljevidih");
        mapLink.setVisible(false);

        previewVideo = new Html("<BR>");
        previewVideo.setVisible(false);


        previewLayout.add(previewImage, previewVideo, exifContainer, mapLink);

        add(leftLayout, previewLayout);
        setFlexGrow(1, leftLayout);
        setFlexGrow(1, previewLayout);
    }

    private static Component createFilterHeader(String labelText,
                                                Consumer<String> filterChangeConsumer) {
        NativeLabel label = new NativeLabel(labelText);
        label.getStyle().set("padding-top", "var(--lumo-space-m)")
                .set("font-size", "var(--lumo-font-size-xs)");
        TextField textField = new TextField();
        textField.setValueChangeMode(ValueChangeMode.EAGER);
        textField.setClearButtonVisible(true);
        textField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        textField.setWidthFull();
        textField.getStyle().set("max-width", "100%");
        textField.addValueChangeListener(
                e -> filterChangeConsumer.accept(e.getValue()));
        VerticalLayout layout = new VerticalLayout(label, textField);
        layout.getThemeList().clear();
        layout.getThemeList().add("spacing-xs");

        return layout;
    }

    private Component renderFileSystemItem(FileSystemItem item) {
        Icon icon = getFileIcon(item);
        String name = getFileName(item);

        HorizontalLayout layout = new HorizontalLayout(icon, new Span(name));
        layout.addClassName("border-layout");
        layout.setWidthFull();
        layout.setAlignItems(Alignment.CENTER);


        return layout;
    }

    private void showVideoPreview(FileSystemItem item) {
        previewImage.setVisible(false); // Hide image if previously shown
        exifContainer.removeAll();
        String videoUrl = "/video/" + item.getName();
        String videoHtml = "<video width='500' height='500' controls><source src='" + videoUrl + "' type='video/mp4'>Your browser does not support the video tag.</video>";
        previewVideo.setVisible(true);

        previewVideo.getElement().setProperty("innerHTML", videoHtml);
    }

    private boolean isVideoFile(String fileName) {
        fileName = fileName.toLowerCase();
        return fileName.endsWith(".mp4") || fileName.endsWith(".webm") || fileName.endsWith(".ogg");
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

    private boolean isImageFile(String fileName) {
        fileName = fileName.toLowerCase();
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif");
    }


    private void showImagePreview(FileSystemItem item) {
        exifContainer.removeAll();
        StreamResource resource = new StreamResource(getFileName(item), () -> s3Service.getFileStream(item.getName()));
        previewImage.setSrc(resource);
        previewImage.setVisible(true);

        try (InputStream inputStream = s3Service.getFileStream(item.getName())) {
            //var div = readExifData(inputStream, getFileName(item));
            var div = readExifDatagrid(inputStream, getFileName(item));
            exifContainer.add(div);
            exifContainer.setVisible(true);

        } catch (IOException e) {

            e.printStackTrace();


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

    private Div readExifData(InputStream inputStream, String fileName) throws IOException {
        Div exifData = new Div();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);


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

                    exifData.add(gpsButton);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Div errorDiv = new Div();
            errorDiv.setText("Error reading EXIF data");
            exifData.add(errorDiv);
        } finally {
            inputStream.close();
        }
        return exifData;

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

    private double convertToDegrees(Rational[] coordinate, String ref) {
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


    private Div readExifDatagrid(InputStream inputStream, String fileName) throws IOException {
        List<ExifData> exifDataList = new ArrayList<>();
        Div container = new Div();
        container.setSizeFull();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            ExifIFD0Directory ifd0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

            if (exifDirectory != null) {
                exifDataList.add(new ExifData("Model kamere", ifd0Directory.getString(ExifIFD0Directory.TAG_MODEL)));
                exifDataList.add(new ExifData("Datum/Čas", exifDirectory.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)));
                exifDataList.add(new ExifData("Širina slike", exifDirectory.getString(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)));
                exifDataList.add(new ExifData("Višina slike", exifDirectory.getString(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)));
                exifDataList.add(new ExifData("ISO", exifDirectory.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)));
                exifDataList.add(new ExifData("Zaslonka", exifDirectory.getString(ExifSubIFDDirectory.TAG_FNUMBER)));
                exifDataList.add(new ExifData("Čas osvetlitve", exifDirectory.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)));
                exifDataList.add(new ExifData("Goriščna razdalja", exifDirectory.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)));
            } else {
                exifDataList.add(new ExifData("No EXIF data found", ""));
            }

            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                double[] latLon = getDecimalCoordinates(gpsDirectory);
                if (latLon != null) {
                    String gpsLink = createGpsLink(latLon[0], latLon[1]);
                    exifDataList.add(new ExifData("GPS Location", gpsLink));
                }
            }
        } catch (Exception e) {
            exifDataList.add(new ExifData("Error reading EXIF data", e.getMessage()));
            e.printStackTrace();
        } finally {
            inputStream.close();
        }

        Grid<ExifData> exifGrid = new Grid<>(ExifData.class, false);
        exifGrid.addColumn(ExifData::getLabel).setClassNameGenerator(item -> "gray-background").setFlexGrow(0).setWidth("200px").setResizable(true).setRenderer(new ComponentRenderer<>(label -> {
            Span span = new Span(label.getLabel());
            span.getStyle().set("font-weight", "bold");

            return span;
        }));

        exifGrid.addColumn(ExifData::getValue).setFlexGrow(1).setResizable(true).setRenderer(new ComponentRenderer<>(label -> {
            if (label.getLabel().equals("GPS Location")) {
                Anchor anchor = new Anchor(label.getValue(), "Koordinate");
                anchor.setTarget("_blank");
                return anchor;
            }
            Span span = new Span(label.getValue());

            return span;
        }));
        exifGrid.setItems(exifDataList);
        exifGrid.setWidthFull();
        container.add(exifGrid);
        return container;
    }

}