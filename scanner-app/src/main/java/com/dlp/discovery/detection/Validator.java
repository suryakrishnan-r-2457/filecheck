package com.dlp.discovery.detection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Validator {
    
    private static final Logger logger = LoggerFactory.getLogger(Validator.class);
    
    private static final int SSN_MIN_AREA = 1;
    private static final int SSN_MAX_AREA = 899;
    private static final int SSN_EXCLUDED_AREA = 666;
    
    private Validator() {
    }
    
    /**
     * Validates a credit card number using the Luhn algorithm (modulo-10 checksum).
     * 
     * @param number the credit card number to validate
     * @return true if the number passes Luhn validation, false otherwise
     */
    public static boolean validateLuhn(String number) {
        if (number == null || number.isEmpty()) {
            logger.debug("Luhn validation failed: null or empty input");
            return false;
        }
        
        String digits = stripNonDigits(number);
        
        if (digits.isEmpty()) {
            logger.debug("Luhn validation failed: no digits found in input");
            return false;
        }
        
        int sum = 0;
        boolean alternate = false;
        
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(digits.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        boolean valid = (sum % 10 == 0);
        logger.debug("Luhn validation for '{}': {}", digits, valid);
        return valid;
    }
    
    /**
     * Validates a Social Security Number for area code validity.
     * Valid area codes are 001-899, excluding 666.
     * 
     * @param ssn the SSN to validate (e.g., "123-45-6789" or "123456789")
     * @return true if the SSN has a valid area code, false otherwise
     */
    public static boolean validateSSN(String ssn) {
        if (ssn == null || ssn.isEmpty()) {
            logger.debug("SSN validation failed: null or empty input");
            return false;
        }
        
        String digits = stripNonDigits(ssn);
        
        if (digits.length() != 9) {
            logger.debug("SSN validation failed: expected 9 digits, got {}", digits.length());
            return false;
        }
        
        int areaCode;
        try {
            areaCode = Integer.parseInt(digits.substring(0, 3));
        } catch (NumberFormatException e) {
            logger.debug("SSN validation failed: unable to parse area code", e);
            return false;
        }
        
        if (areaCode < SSN_MIN_AREA || areaCode > SSN_MAX_AREA || areaCode == SSN_EXCLUDED_AREA) {
            logger.debug("SSN validation failed: invalid area code {}", areaCode);
            return false;
        }
        
        logger.debug("SSN validation passed for area code {}", areaCode);
        return true;
    }
    
    /**
     * Validates the context around a match to reduce false positives.
     * Checks for indicators that the match might not be sensitive data.
     * 
     * @param text the full text containing the match
     * @param matchOffset the starting offset of the match in the text
     * @param matchLength the length of the matched content
     * @return true if the context is valid, false if it appears to be a false positive
     */
    public static boolean validateContext(String text, int matchOffset, int matchLength) {
        if (text == null || text.isEmpty()) {
            logger.debug("Context validation failed: null or empty text");
            return false;
        }
        
        if (matchOffset < 0 || matchLength <= 0 || matchOffset + matchLength > text.length()) {
            logger.debug("Context validation failed: invalid offset {} or length {}", matchOffset, matchLength);
            return false;
        }
        
        int contextStart = Math.max(0, matchOffset - 20);
        int contextEnd = Math.min(text.length(), matchOffset + matchLength + 20);
        String context = text.substring(contextStart, contextEnd).toLowerCase();
        
        String[] falsePositiveIndicators = {
            "example", "test", "sample", "dummy", "fake", "mock",
            "placeholder", "xxx", "000-00-0000", "123-45-6789",
            "0000-0000-0000-0000", "1234-5678-9012-3456"
        };
        
        for (String indicator : falsePositiveIndicators) {
            if (context.contains(indicator)) {
                logger.debug("Context validation failed: found false positive indicator '{}'", indicator);
                return false;
            }
        }
        
        logger.debug("Context validation passed for match at offset {}", matchOffset);
        return true;
    }
    
    /**
     * Strips all non-digit characters from a string.
     * 
     * @param input the input string
     * @return a string containing only digits, or empty string if input is null
     */
    public static String stripNonDigits(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[^0-9]", "");
    }
}
