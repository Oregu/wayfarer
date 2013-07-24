package com.wavedroid.wayfarer;

import java.util.Arrays;
import java.util.Random;

import fi.foyt.foursquare.api.FoursquareApi;
import fi.foyt.foursquare.api.FoursquareApiException;
import fi.foyt.foursquare.api.Result;
import fi.foyt.foursquare.api.entities.Checkin;
import fi.foyt.foursquare.api.entities.CompactVenue;
import fi.foyt.foursquare.api.entities.CompleteVenue;
import fi.foyt.foursquare.api.entities.Location;
import fi.foyt.foursquare.api.entities.VenuesSearchResult;

import static com.wavedroid.wayfarer.WayfarerProperties.getAccessToken;
import static com.wavedroid.wayfarer.WayfarerProperties.getClientId;
import static com.wavedroid.wayfarer.WayfarerProperties.getClientSecret;
import static com.wavedroid.wayfarer.WayfarerProperties.getRedirectUrl;
import static com.wavedroid.wayfarer.WayfarerProperties.getStartVenueId;
import static com.wavedroid.wayfarer.WayfarerProperties.isDebug;

/**
 * @author DKhvatov
 */
public class Wayfarer {


    public static void main(String[] args) throws FoursquareApiException, InterruptedException {

        double latOffset = 0.005;
        double lonOffset = 0.005;

        FoursquareApi api = new FoursquareApi(getClientId(), getClientSecret(), getRedirectUrl());
        api.setoAuthToken(getAccessToken());

        Result<CompleteVenue> venueResult = api.venue(getStartVenueId());
        CompleteVenue venue = venueResult.getResult();
        Random rnd = new Random(Math.abs(Wayfarer.class.hashCode()));
        while (!Thread.interrupted()) {
            if (!isDebug()) {
                Result<Checkin> checkinResult = api.checkinsAdd(venue.getId(), null, null, "public", getLatLon(venue, 0.0, 0.0), 1.0, 0.0, 1.0);
                if (checkinResult.getMeta().getCode() != 200) {
                    System.out.println("checkin error: " + checkinResult.getMeta().getErrorDetail() + "(" + checkinResult.getMeta().getErrorType() + ")");
                }
            }

            venue = nextVenue(api, rnd, venue, latOffset, lonOffset, 0);

            if (!isDebug())
                Thread.sleep(rnd.nextInt(1440000) + 360000);
        }
        System.out.println("Starved to death :(");
    }

    private static CompleteVenue nextVenue(FoursquareApi api, Random rnd, CompleteVenue venue, double latOffset, double lonOffset, int counter) throws FoursquareApiException {
        System.out.println(tab(counter) + "searching for next venue, current venue: " + printVenue(venue) + ", lat step: " + latOffset + ", lon step: " + lonOffset);
        if (counter > 20) {
            Thread.currentThread().interrupt();
            return null; // give up
        }
        String ll = getLatLon(venue, latOffset, lonOffset);
        Result<VenuesSearchResult> vsr = api.venuesSearch(ll, 1.0, 0.0, 1.0, "", 50, "checkin", null, null, null, null);
        VenuesSearchResult searchResult = vsr.getResult();
        if (searchResult == null) {
            System.out.println(tab(counter) + "Search error, waiting");
            try {
                Thread.sleep(10000 * counter);
            } catch (InterruptedException ignored) {
            }
            return nextVenue(api, rnd, venue, latOffset, lonOffset, ++counter);
        }
        CompactVenue[] venues = searchResult.getVenues();
        if (venues.length == 0) {
            System.out.println(tab(counter) + "Nothing found, waiting + increasing step");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            return nextVenue(api, rnd, venue, latOffset * 1.25, lonOffset * 1.25, ++counter);
        }
        CompactVenue compactVenue = venues[rnd.nextInt(venues.length)];
        Result<CompleteVenue> venueResult = api.venue(compactVenue.getId());
        CompleteVenue result = venueResult.getResult();
        if (result.getBeenHere().getCount() > 0) {
            System.out.println(tab(counter) + "Never repeat yourself");
            return nextVenue(api, rnd, venue, latOffset, lonOffset, ++counter);
        }
        System.out.println(tab(counter) + "next venue: " + result.getName());
        return result;
    }

    private static String printVenue(CompleteVenue venue) {
        Location loc = venue.getLocation();
        if (loc == null) return venue.getName();
        return venue.getName() + " [" + loc.getCity() + ", " + loc.getCountry() + "]";
    }

    private static String tab(int w) {
        byte[] b = new byte[w * 2];
        Arrays.fill(b, (byte) 0x20);
        return new String(b);
    }

    private static String getLatLon(CompleteVenue venue, double latOffset, double lonOffset) {
        if (venue == null) return "";
        Location loc = venue.getLocation();
        if (loc == null) return "";
        if (loc.getLat() == null) return "";
        if (loc.getLng() == null) return "";
        return ((loc.getLat() + latOffset) + "," + (loc.getLng() + lonOffset));
    }

}