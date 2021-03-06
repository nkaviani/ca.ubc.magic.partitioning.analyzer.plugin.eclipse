package plugin.mvc.adapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class 
Callback 
{
	private final String 		method_name;
	private final List<Class<?>> 	parameters;
	
	public Callback
	( String method_name, Class<?>... parameters )
	{
		this.method_name
			= method_name;
		this.parameters
			= Collections.unmodifiableList( Arrays.asList(parameters) );
	}
	
	public String 
	getName()
	{
		return this.method_name;
	}
	
	public List<Class<?>>
	getParameters()
	{
		return this.parameters;
	}
}
