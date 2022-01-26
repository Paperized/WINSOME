package it.winsome.common.validation;

import it.winsome.common.entity.enums.CurrencyType;
import it.winsome.common.entity.enums.VoteType;
import it.winsome.common.exception.InvalidParameterException;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All in one class with Consumer<T> type of functions which can be used with the NetMessage class to
 * validate any input read from a message, eventually throwing an InvalidParameterException exception
 */
public class Validator {
    public static final String usernameRegex = "^[a-zA-Z0-9._-]{3,15}$";
    private static final Pattern pattern = Pattern.compile(usernameRegex);

    public static void validateCurrencyType(String currency) throws InvalidParameterException {
        if(currency == null || currency.length() == 0
                || CurrencyType.fromString(currency) == null)
            throw new InvalidParameterException("The vote must be wincoin or btc!");
    }

    public static void validateCurrencyType(Integer currency) throws InvalidParameterException {
        if(currency == null)
            throw new InvalidParameterException("The vote must be wincoin or btc!");
    }

    public static void validateCommentContent(String content) throws InvalidParameterException {
        if(content == null || content.length() == 0)
            throw new InvalidParameterException("The comment content was not provided!");

        if(content.length() > 150)
            throw new InvalidParameterException("The comment must be less then 150 characters!");
    }

    public static void validateVoteType(String vote) throws InvalidParameterException {
        if(vote == null || vote.length() == 0
            || VoteType.fromString(vote) == null)
            throw new InvalidParameterException("The vote must be +1 in case of Upvote or -1 if Downvote!");
    }

    public static void validateVotableType(Integer vote) throws InvalidParameterException {
        if(vote == null)
            throw new InvalidParameterException("The votable type must be valid!");
    }

    public static void validateVoteType(Integer vote) throws InvalidParameterException {
        if(vote == null)
            throw new InvalidParameterException("The vote must be +1 in case of Upvote or -1 if Downvote!");
    }

    public static void validatePostTitle(String title) throws InvalidParameterException {
        if(title == null || title.length() == 0)
            throw new InvalidParameterException("The post title was not provided!");

        if(title.length() > 20)
            throw new InvalidParameterException("The post title must be less then 20 characters!");
    }

    public static void validatePostContent(String content) throws InvalidParameterException {
        if(content == null || content.length() == 0)
            throw new InvalidParameterException("The post title was not provided!");

        if(content.length() > 500)
            throw new InvalidParameterException("The post title must be less then 500 characters!");
    }

    public static void validatePage(Integer page) throws InvalidParameterException {
        if(page == null || page < 0)
            throw new InvalidParameterException("The page number must be positive (>= 0)!");
    }

    public static void validatePostId(Integer id) throws InvalidParameterException {
        if(validateEntityId(id))
            throw new InvalidParameterException("The post id must be positive (>= 0)");
    }

    public static void validateCommentId(Integer id) throws InvalidParameterException {
        if(id == null || id < 0)
            throw new InvalidParameterException("The comment id must be positive (>= 0)!");
    }

    private static boolean validateEntityId(Integer id) {
        return id == null || id < 0;
    }

    public static void validateUsername(String username) throws InvalidParameterException {
        if(username == null || username.length() < 3)
            throw new InvalidParameterException("The username must be bigger then 3 characters!");
        if(username.length() > 15)
            throw new InvalidParameterException("The username must be less then 16 characters!");

        Matcher matcher = pattern.matcher(username);
        if(!matcher.matches()) {
            throw new InvalidParameterException("The username must contain only alphanumeric characters or dot(.) underscore(_) hyphen(-)!");
        }
    }

    public static void validatePassword(String password) throws InvalidParameterException {
        if(password == null || password.length() != 64)
            throw new InvalidParameterException("The password must be hashed in SHA-512!");
    }

    public static void validateTags(Collection<String> tags) throws InvalidParameterException {
        if(tags == null || tags.size() == 0) throw new InvalidParameterException("Tags must be at least 1!");
        int tagsDetected = 0;
        for(String tag : tags) {
            if(tag != null && !tag.trim().equals("")) {
                tagsDetected++;
            }
        }

        if(tagsDetected == 0) throw new InvalidParameterException("Tags must be at least 1!");
        if(tagsDetected > 5) throw new InvalidParameterException("Tags cannot be more then 5!");
    }
}
