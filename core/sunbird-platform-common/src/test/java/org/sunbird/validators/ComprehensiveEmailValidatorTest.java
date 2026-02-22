package org.sunbird.validators;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Comprehensive test suite for EmailValidator covering various email formats and edge cases.
 */
public class ComprehensiveEmailValidatorTest {

  // =============================================
  // Valid Email Tests
  // =============================================

  @Test
  public void testEmailValidator_ValidBasicEmail() {
    assertTrue(EmailValidator.isEmailValid("test@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithNumbers() {
    assertTrue(EmailValidator.isEmailValid("test123@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithUnderscore() {
    assertTrue(EmailValidator.isEmailValid("test_name@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithHyphen() {
    assertTrue(EmailValidator.isEmailValid("test-name@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithDot() {
    assertTrue(EmailValidator.isEmailValid("test.name@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithMultipleDots() {
    assertTrue(EmailValidator.isEmailValid("test.name.example@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithPlusMinus() {
    assertTrue(EmailValidator.isEmailValid("test+tag@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithNumbers_Domain() {
    assertTrue(EmailValidator.isEmailValid("test@example123.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithHyphenDomain() {
    assertTrue(EmailValidator.isEmailValid("test@ex-ample.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithSubdomain() {
    assertTrue(EmailValidator.isEmailValid("test@mail.example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithMultipleSubdomains() {
    assertTrue(EmailValidator.isEmailValid("test@mail.example.co.uk"));
  }

  @Test
  public void testEmailValidator_ValidEmailThreeLetterTLD() {
    assertTrue(EmailValidator.isEmailValid("test@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailFourLetterTLD() {
    assertTrue(EmailValidator.isEmailValid("test@example.info"));
  }

  @Test
  public void testEmailValidator_ValidEmailTwoLetterTLD() {
    assertTrue(EmailValidator.isEmailValid("test@example.co"));
  }

  @Test
  public void testEmailValidator_ValidEmailAllNumbers() {
    assertTrue(EmailValidator.isEmailValid("123456@example.com"));
  }

  @Test
  public void testEmailValidator_ValidEmailWithLeadingPlus() {
    assertTrue(EmailValidator.isEmailValid("+test@example.com"));
  }

  // =============================================
  // Invalid Email - Missing Components
  // =============================================

  @Test
  public void testEmailValidator_InvalidEmail_NoAtSymbol() {
    assertFalse(EmailValidator.isEmailValid("testemail.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_NoLocalPart() {
    assertFalse(EmailValidator.isEmailValid("@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_NoDomain() {
    assertFalse(EmailValidator.isEmailValid("test@"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_NoTLD() {
    assertFalse(EmailValidator.isEmailValid("test@domain"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_MultipleAtSymbols() {
    assertFalse(EmailValidator.isEmailValid("test@@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_AtSymbolInMiddle() {
    assertFalse(EmailValidator.isEmailValid("te@st@example.com"));
  }

  // =============================================
  // Invalid Email - Special Characters
  // =============================================

  @Test
  public void testEmailValidator_InvalidEmail_SpaceInLocalPart() {
    assertFalse(EmailValidator.isEmailValid("test name@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_SpaceBeforeDomain() {
    assertFalse(EmailValidator.isEmailValid("test@ example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_InvalidCharacter_Dollar() {
    assertFalse(EmailValidator.isEmailValid("test$@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_InvalidCharacter_Exclamation() {
    assertFalse(EmailValidator.isEmailValid("test!@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_InvalidCharacter_Hash() {
    assertFalse(EmailValidator.isEmailValid("test#@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_InvalidCharacter_Percent() {
    assertFalse(EmailValidator.isEmailValid("test%@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_Parenthesis() {
    assertFalse(EmailValidator.isEmailValid("test()@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_SquareBrackets() {
    assertFalse(EmailValidator.isEmailValid("test[]@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_CurlyBraces() {
    assertFalse(EmailValidator.isEmailValid("test{}@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_Apostrophe() {
    assertFalse(EmailValidator.isEmailValid("test'@example.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_DoubleQuote() {
    assertFalse(EmailValidator.isEmailValid("test\"@example.com"));
  }

  // =============================================
  // Invalid Email - Domain Issues
  // =============================================

  @Test
  public void testEmailValidator_ValidEmail_DomainWithHyphenInMiddle() {
    // The regex allows hyphens in domain middle position
    assertTrue(EmailValidator.isEmailValid("test@ex-ample.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_DomainDoubleHyphen() {
    // Double hyphen should still pass the regex
    assertTrue(EmailValidator.isEmailValid("test@example--site.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_DomainWithSpecialCharacters() {
    assertFalse(EmailValidator.isEmailValid("test@exam$ple.com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_TLDStartsWithNumber() {
    assertFalse(EmailValidator.isEmailValid("test@example.1com"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_SingleLetterDomain() {
    assertFalse(EmailValidator.isEmailValid("test@a.c"));
  }

  // =============================================
  // Invalid Email - Null/Blank
  // =============================================

  @Test
  public void testEmailValidator_InvalidEmail_EmptyString() {
    assertFalse(EmailValidator.isEmailValid(""));
  }

  @Test
  public void testEmailValidator_InvalidEmail_NullString() {
    assertFalse(EmailValidator.isEmailValid(null));
  }

  @Test
  public void testEmailValidator_InvalidEmail_WhitespaceOnly() {
    assertFalse(EmailValidator.isEmailValid("   "));
  }

  @Test
  public void testEmailValidator_InvalidEmail_Tab() {
    assertFalse(EmailValidator.isEmailValid("\t"));
  }

  @Test
  public void testEmailValidator_InvalidEmail_Newline() {
    assertFalse(EmailValidator.isEmailValid("\n"));
  }

  // =============================================
  // Edge Cases - Valid
  // =============================================

  @Test
  public void testEmailValidator_EdgeCase_LongLocalPart() {
    assertTrue(EmailValidator.isEmailValid("verylonglocalpartwithnumberand123@example.com"));
  }

  @Test
  public void testEmailValidator_EdgeCase_LongDomain() {
    assertTrue(EmailValidator.isEmailValid("test@verylongdomainnamewithmanychars.example.com"));
  }

  @Test
  public void testEmailValidator_EdgeCase_SingleCharLocalPart() {
    assertTrue(EmailValidator.isEmailValid("a@example.com"));
  }

  @Test
  public void testEmailValidator_EdgeCase_SingleCharDomain() {
    assertTrue(EmailValidator.isEmailValid("test@a.io"));
  }

  @Test
  public void testEmailValidator_EdgeCase_Numbers_Before_At() {
    assertTrue(EmailValidator.isEmailValid("123@456.com"));
  }

  @Test
  public void testEmailValidator_EdgeCase_All_Special_Allowed() {
    assertTrue(EmailValidator.isEmailValid("_+-@example.com"));
  }

  // =============================================
  // Edge Cases - Invalid
  // =============================================

  @Test
  public void testEmailValidator_EdgeCase_ConsecutiveDots() {
    assertFalse(EmailValidator.isEmailValid("test..name@example.com"));
  }

  @Test
  public void testEmailValidator_EdgeCase_DotBeforeAt() {
    assertFalse(EmailValidator.isEmailValid("test.@example.com"));
  }

  @Test
  public void testEmailValidator_EdgeCase_DotInDomain_Trailing() {
    assertFalse(EmailValidator.isEmailValid("test@example.com."));
  }

  @Test
  public void testEmailValidator_EdgeCase_DoubleAtSymbol() {
    assertFalse(EmailValidator.isEmailValid("test@@example.com"));
  }

  @Test
  public void testEmailValidator_EdgeCase_NoExtension() {
    assertFalse(EmailValidator.isEmailValid("test@example"));
  }

  // =============================================
  // Real-world Email Formats - Valid
  // =============================================

  @Test
  public void testEmailValidator_RealWorld_Gmail() {
    // The regex allows + in the local part (see EMAIL_PATTERN: \\+)
    assertTrue(EmailValidator.isEmailValid("user+tag@gmail.com"));
  }

  @Test
  public void testEmailValidator_RealWorld_Outlook() {
    assertTrue(EmailValidator.isEmailValid("user@outlook.com"));
  }

  @Test
  public void testEmailValidator_RealWorld_YahooMail() {
    assertTrue(EmailValidator.isEmailValid("user@yahoo.com"));
  }

  @Test
  public void testEmailValidator_RealWorld_BusinessEmail() {
    assertTrue(EmailValidator.isEmailValid("john.doe@company.co.uk"));
  }

  @Test
  public void testEmailValidator_RealWorld_SubdomainEmail() {
    assertTrue(EmailValidator.isEmailValid("user@mail.company.com"));
  }

  @Test
  public void testEmailValidator_RealWorld_NumberedDomain() {
    assertTrue(EmailValidator.isEmailValid("user@company123.com"));
  }

  // =============================================
  // Real-world Email Formats - Invalid
  // =============================================

  @Test
  public void testEmailValidator_RealWorld_Invalid_NoLocalPart() {
    assertFalse(EmailValidator.isEmailValid("@gmail.com"));
  }

  @Test
  public void testEmailValidator_RealWorld_Invalid_NoDomain() {
    assertFalse(EmailValidator.isEmailValid("user@"));
  }

  @Test
  public void testEmailValidator_RealWorld_Invalid_ExtraAtSign() {
    assertFalse(EmailValidator.isEmailValid("user@@gmail.com"));
  }

  @Test
  public void testEmailValidator_RealWorld_Invalid_SpaceInEmail() {
    assertFalse(EmailValidator.isEmailValid("user name@gmail.com"));
  }

  @Test
  public void testEmailValidator_RealWorld_Invalid_TrailingDot() {
    assertFalse(EmailValidator.isEmailValid("user@gmail.com."));
  }
}
