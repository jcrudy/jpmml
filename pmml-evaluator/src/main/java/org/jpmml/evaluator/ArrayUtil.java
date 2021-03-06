/*
 * Copyright (c) 2012 University of Tartu
 */
package org.jpmml.evaluator;

import java.util.*;

import org.jpmml.manager.*;

import org.dmg.pmml.*;

import com.google.common.collect.*;

public class ArrayUtil {

	private ArrayUtil(){
	}

	static
	public Boolean isIn(Array array, Object value){
		List<String> values = getContent(array);

		validateDataType(value);

		String stringValue = (String)ParameterUtil.cast(DataType.STRING, value);

		boolean result = values.contains(stringValue);

		return Boolean.valueOf(result);
	}

	static
	public Boolean isNotIn(Array array, Object value){
		List<String> values = getContent(array);

		validateDataType(value);

		String stringValue = (String)ParameterUtil.cast(DataType.STRING, value);

		boolean result = !values.contains(stringValue);

		return Boolean.valueOf(result);
	}

	static
	public List<String> getContent(Array array){
		List<String> values = array.getContent();

		if(values == null){
			values = tokenize(array);

			array.setContent(values);
		}

		return values;
	}

	static
	public List<String> tokenize(Array array){
		List<String> result;

		Array.Type type = array.getType();
		switch(type){
			case INT:
			case REAL:
				result = tokenize(array.getValue(), false);
				break;
			case STRING:
				result = tokenize(array.getValue(), true);
				break;
			default:
				throw new UnsupportedFeatureException(array, type);
		}

		Number n = array.getN();
		if(n != null && n.intValue() != result.size()){
			throw new InvalidFeatureException(array);
		}

		return result;
	}

	static
	public List<String> tokenize(String string, boolean enableQuotes){
		List<String> result = Lists.newArrayList();

		StringBuilder sb = new StringBuilder();

		boolean quoted = false;

		tokens:
		for(int i = 0; i < string.length(); i++){
			char c = string.charAt(i);

			if(quoted){

				if(c == '\\' && i < (string.length() - 1)){
					c = string.charAt(i + 1);

					if(c == '\"'){
						sb.append('\"');

						i++;
					} else

					{
						sb.append('\\');
					}

					continue tokens;
				} // End if

				sb.append(c);

				if(c == '\"'){
					result.add(createToken(sb, enableQuotes));

					quoted = false;
				}
			} else

			{
				if(c == '\"' && enableQuotes){

					if(sb.length() > 0){
						result.add(createToken(sb, enableQuotes));
					}

					sb.append('\"');

					quoted = true;
				} else

				if(Character.isWhitespace(c)){

					if(sb.length() > 0){
						result.add(createToken(sb, enableQuotes));
					}
				} else

				{
					sb.append(c);
				}
			}
		}

		if(sb.length() > 0){
			result.add(createToken(sb, enableQuotes));
		}

		return Collections.unmodifiableList(result);
	}

	static
	private String createToken(StringBuilder sb, boolean enableQuotes){
		String result;

		if(sb.length() > 1 && (sb.charAt(0) == '\"' && sb.charAt(sb.length() - 1) == '\"') && enableQuotes){
			result = sb.substring(1, sb.length() - 1);
		} else

		{
			result = sb.substring(0, sb.length());
		}

		sb.setLength(0);

		return result;
	}

	static
	private void validateDataType(Object value){
		DataType dataType = ParameterUtil.getDataType(value);

		switch(dataType){
			case STRING:
			case INTEGER:
				break;
			case FLOAT:
			case DOUBLE:
				// Falls through
			default:
				throw new EvaluationException();
		}
	}
}