package com.aemotionalline.domain.constraint;

public class Constraint
{
	private final String description;
	
	  public Constraint(String description) 
	  {
	        if (description == null || description.isBlank())
	        {
	            throw new IllegalArgumentException("Constraint description cannot be blank");
	        }
	        this.description = description;
	    }

	  public String getDescription()
	  {
		  return description;
	  }
	  
	  
}
