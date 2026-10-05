package com.aemotionalline.domain.couple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.Ids;


public class CoupleTest
{
	
    @Test
    void shouldNotCreateCoupleWithSamePartners() 
    {
        UserId partner = Ids.user(10L);
        UserId therapist = Ids.user(30L);

        assertThrows(DomainException.class, () -> new Couple(Ids.couple(1L), partner, partner, therapist));
    }
    
    @Test
    void shouldRecognizePartner()
    {
        UserId partnerOne = Ids.user(10L);
        UserId partnerTwo = Ids.user(20L);
        UserId therapist = Ids.user(30L);
        
        UserId notPartner = Ids.user(45L);

        var couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
        
        assertTrue(couple.isPartner(partnerOne));
        assertTrue(couple.isPartner(partnerTwo));
        assertFalse(couple.isPartner(therapist));
        assertFalse(couple.isPartner(notPartner));
    }
    
    @Test
    void shouldRecognizeTherapist()
    {
        UserId partnerOne = Ids.user(10L);
        UserId partnerTwo = Ids.user(20L);
        UserId therapist = Ids.user(30L);
        
        UserId notTherapist = Ids.user(45L);

        var couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
        
        assertFalse(couple.isTherapist(partnerOne));
        assertFalse(couple.isTherapist(partnerTwo));
        assertTrue(couple.isTherapist(therapist));
        assertFalse(couple.isTherapist(notTherapist));
    }
    
    @Test
    void shouldRequireAllElementsToCreateCouple()
    {
        UserId partnerOne = Ids.user(10L);
        UserId partnerTwo = Ids.user(20L);
        UserId therapist = Ids.user(30L);
        
        assertThrows(DomainException.class, () -> new Couple(null, partnerOne, partnerTwo, therapist));
        assertThrows(DomainException.class, () -> new Couple(Ids.couple(1L), null, partnerTwo, therapist));
        assertThrows(DomainException.class, () -> new Couple(Ids.couple(1L), partnerOne, null, therapist));
        assertThrows(DomainException.class, () -> new Couple(Ids.couple(1L), partnerOne, partnerTwo, null));
    }
    
    @Test
    void shouldNotCreateCoupleWhenTherapistIsAlsoAPartner()
    {
        UserId partnerOne = Ids.user(10L);
        UserId partnerTwo = Ids.user(20L);
        
        assertThrows(DomainException.class, () -> new Couple(Ids.couple(1L), partnerOne, partnerTwo, partnerOne));
        assertThrows(DomainException.class, () -> new Couple(Ids.couple(1L), partnerOne, partnerTwo, partnerTwo));
    }
    
    @Test
    void shouldBeEqualWhenIdsAreEqual()
    {
        var couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
        var sameCoupleLoadedAgain = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
        
        assertEquals(couple, sameCoupleLoadedAgain);
        assertEquals(couple.hashCode(), sameCoupleLoadedAgain.hashCode());
    }
    
    @Test
    void shouldNotBeEqualWhenIdsAreDifferent()
    {
        var couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
        var otherCouple = new Couple(Ids.couple(2L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
        
        assertNotEquals(couple, otherCouple);
    }
}
