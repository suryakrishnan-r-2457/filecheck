package com.dlp.discovery.parsing;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TikaParserService {

    private static final Logger logger = LoggerFactory.getLogger(TikaParserService.class);

    private final ParserConfig config;
    private final Parser parser;

    public TikaParserService(ParserConfig config) {
        this.config = config;
        this.parser = new AutoDetectParser();
        logger.debug("TikaParserService initialized with config: timeoutSeconds={}", 
                config.getTimeoutSeconds());
    }

    public TextExtractionResult parse(Path filePath) {
        logger.debug("Starting text extraction for file: {}", filePath);

        if (!Files.exists(filePath)) {
            logger.error("File does not exist: {}", filePath);
            return TextExtractionResult.failed(null, "File does not exist");
        }

        if (!Files.isReadable(filePath)) {
            logger.error("File is not readable: {}", filePath);
            return TextExtractionResult.failed(null, "File is not readable");
        }

        Metadata metadata = new Metadata();
        
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(filePath))) {
            metadata.set("resourceName", filePath.getFileName().toString());
            
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);

            try {
                parser.parse(stream, handler, metadata, context);
                
                String extractedText = handler.toString();
                String mimeType = metadata.get(Metadata.CONTENT_TYPE);
                
                logger.debug("Successfully extracted text from file: {}, mimeType: {}, textLength: {}", 
                        filePath, mimeType, extractedText != null ? extractedText.length() : 0);

                TextExtractionResult.Builder resultBuilder = TextExtractionResult.builder()
                        .extractedText(extractedText)
                        .mimeType(mimeType)
                        .status(TextExtractionResult.ParseStatus.OK)
                        .metadata(extractMetadataMap(metadata));

                Integer pageCount = extractPageCount(metadata, mimeType);
                if (pageCount != null) {
                    resultBuilder.pageCount(pageCount);
                    logger.debug("Detected page count: {} for file: {}", pageCount, filePath);
                }

                return resultBuilder.build();

            } catch (org.apache.tika.exception.EncryptedDocumentException e) {
                logger.warn("File is encrypted: {}", filePath, e);
                String mimeType = metadata.get(Metadata.CONTENT_TYPE);
                return TextExtractionResult.encrypted(mimeType);

            } catch (org.apache.tika.exception.TikaException e) {
                logger.error("Tika parsing error for file: {}", filePath, e);
                String mimeType = metadata.get(Metadata.CONTENT_TYPE);
                return TextExtractionResult.failed(mimeType, 
                        "Tika parsing error: " + e.getMessage());

            } catch (org.xml.sax.SAXException e) {
                logger.error("SAX parsing error for file: {}", filePath, e);
                String mimeType = metadata.get(Metadata.CONTENT_TYPE);
                return TextExtractionResult.failed(mimeType, 
                        "SAX parsing error: " + e.getMessage());
            }

        } catch (IOException e) {
            logger.error("IO error while parsing file: {}", filePath, e);
            return TextExtractionResult.failed(null, "IO error: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Unexpected error while parsing file: {}", filePath, e);
            return TextExtractionResult.failed(null, 
                    "Unexpected error: " + e.getMessage());
        }
    }

    private Map<String, String> extractMetadataMap(Metadata metadata) {
        Map<String, String> metadataMap = new HashMap<>();
        
        for (String name : metadata.names()) {
            String value = metadata.get(name);
            if (value != null && !value.trim().isEmpty()) {
                metadataMap.put(name, value);
            }
        }

        String author = metadata.get(TikaCoreProperties.CREATOR);
        if (author != null && !author.trim().isEmpty()) {
            metadataMap.put("author", author);
        }

        String title = metadata.get(TikaCoreProperties.TITLE);
        if (title != null && !title.trim().isEmpty()) {
            metadataMap.put("title", title);
        }

        logger.debug("Extracted {} metadata fields", metadataMap.size());
        return metadataMap;
    }

    private Integer extractPageCount(Metadata metadata, String mimeType) {
        if (mimeType == null) {
            return null;
        }

        if (mimeType.toLowerCase().contains("pdf")) {
            String pageCountStr = metadata.get("xmpTPg:NPages");
            if (pageCountStr == null) {
                pageCountStr = metadata.get("pdf:docinfo:pages");
            }
            if (pageCountStr == null) {
                pageCountStr = metadata.get("meta:page-count");
            }
            if (pageCountStr == null) {
                pageCountStr = metadata.get("Page-Count");
            }

            if (pageCountStr != null) {
                try {
                    return Integer.parseInt(pageCountStr.trim());
                } catch (NumberFormatException e) {
                    logger.debug("Failed to parse page count: {}", pageCountStr);
                }
            }
        }

        return null;
    }
}
