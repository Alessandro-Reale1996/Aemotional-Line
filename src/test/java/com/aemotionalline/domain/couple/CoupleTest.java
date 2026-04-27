package com.aemotionalline.domain.couple;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;


public class CoupleTest
{
	
    @Test
    void shouldNotCreateCoupleWithSamePartners() 
    {
        UserId partner = new UserId(10L);
        UserId therapist = new UserId(30L);

        assertThrows(DomainException.class, () -> new Couple(1L, partner, partner, therapist));
    }
    
    @Test
    void shouldReconizePartner()
    {
        UserId partnerOne = new UserId(10L);
        UserId partnerTwo = new UserId(10L);
        UserId therapist = new UserId(30L);
        
        UserId notPartner = new UserId(45L);

        var couple = new Couple(1L, partnerOne, partnerTwo, therapist);
        
        assertTrue(couple.isPartner(partnerOne));
        assertTrue(couple.isPartner(partnerTwo));
        assertFalse(couple.isPartner(therapist));
        assertFalse(couple.isPartner(notPartner));
    }
    
    @Test
    void shouldReconizeTherapist()
    {
        UserId partnerOne = new UserId(10L);
        UserId partnerTwo = new UserId(10L);
        UserId therapist = new UserId(30L);
        
        UserId notTherapist = new UserId(45L);

        var couple = new Couple(1L, partnerOne, partnerTwo, therapist);
        
        assertFalse(couple.isTherapist(partnerOne));
        assertFalse(couple.isTherapist(partnerTwo));
        assertTrue(couple.isTherapist(therapist));
        assertFalse(couple.isPartner(notTherapist));
    }
    
    @Test
    void schouldCreateCoupleWithAllElements()
    {
        UserId partnerOne = new UserId(10L);
        UserId partnerTwo = new UserId(10L);
        UserId therapist = new UserId(30L);
        
        assertThrows(NullPointerException.class, () -> new Couple(null, partnerOne, partnerTwo, therapist));
        assertThrows(NullPointerException.class, () -> new Couple(1L, null, partnerTwo, therapist));
        assertThrows(NullPointerException.class, () -> new Couple(1L, partnerOne, null, therapist));
        assertThrows(NullPointerException.class, () -> new Couple(1L, partnerOne, partnerTwo, null));
    }
}
