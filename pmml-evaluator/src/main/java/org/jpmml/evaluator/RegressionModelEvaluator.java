/*
 * Copyright (c) 2012 University of Tartu
 */
package org.jpmml.evaluator;

import java.util.*;

import org.jpmml.manager.*;

import org.dmg.pmml.*;

public class RegressionModelEvaluator extends RegressionModelManager implements Evaluator {

	public RegressionModelEvaluator(PMML pmml){
		super(pmml);
	}

	public RegressionModelEvaluator(PMML pmml, RegressionModel regressionModel){
		super(pmml, regressionModel);
	}

	public RegressionModelEvaluator(RegressionModelManager parent){
		this(parent.getPmml(), parent.getModel());
	}

	@Override
	public Object prepare(FieldName name, Object value){
		return ParameterUtil.prepare(getDataField(name), getMiningField(name), value);
	}

	@Override
	public Map<FieldName, ?> evaluate(Map<FieldName, ?> arguments){
		RegressionModel regressionModel = getModel();
		if(!regressionModel.isScorable()){
			throw new InvalidResultException(regressionModel);
		}

		Map<FieldName, ?> predictions;

		ModelManagerEvaluationContext context = new ModelManagerEvaluationContext(this, arguments);

		MiningFunctionType miningFunction = regressionModel.getFunctionName();
		switch(miningFunction){
			case REGRESSION:
				predictions = evaluateRegression(context);
				break;
			case CLASSIFICATION:
				predictions = evaluateClassification(context);
				break;
			default:
				throw new UnsupportedFeatureException(regressionModel, miningFunction);
		}

		return OutputUtil.evaluate(predictions, context);
	}

	Map<FieldName, Double> evaluateRegression(EvaluationContext context){
		RegressionModel regressionModel = getModel();

		List<RegressionTable> regressionTables = getRegressionTables();
		if(regressionTables.size() != 1){
			throw new InvalidFeatureException(regressionModel);
		}

		RegressionTable regressionTable = regressionTables.get(0);

		Double value = evaluateRegressionTable(regressionTable, context);
		if(value != null){
			value = normalizeRegressionResult(regressionModel, value);
		}

		FieldName targetField = getTargetField();

		return Collections.singletonMap(targetField, value);
	}

	Map<FieldName, ClassificationMap> evaluateClassification(EvaluationContext context){
		RegressionModel regressionModel = getModel();

		List<RegressionTable> regressionTables = getRegressionTables();
		if(regressionTables.size() < 1){
			throw new InvalidFeatureException(regressionModel);
		}

		double sumExp = 0d;

		ClassificationMap result = new ClassificationMap();

		for(RegressionTable regressionTable : regressionTables){
			String category = regressionTable.getTargetCategory();
			if(category == null){
				throw new InvalidFeatureException(regressionTable);
			}

			Double value = evaluateRegressionTable(regressionTable, context);
			if(value == null){
				throw new MissingResultException(regressionTable);
			}

			sumExp += Math.exp(value.doubleValue());

			result.put(category, value);
		}

		FieldName targetField = getTargetField();

		DataField dataField = getDataField(targetField);

		OpType opType = dataField.getOptype();
		switch(opType){
			case CATEGORICAL:
				break;
			default:
				throw new UnsupportedFeatureException(dataField, opType);
		}

		Collection<Map.Entry<String, Double>> entries = result.entrySet();
		for(Map.Entry<String, Double> entry : entries){
			entry.setValue(normalizeClassificationResult(regressionModel, entry.getValue(), sumExp));
		}

		return Collections.singletonMap(targetField, result);
	}

	static
	private Double evaluateRegressionTable(RegressionTable regressionTable, EvaluationContext context){
		double result = 0D;

		result += regressionTable.getIntercept();

		List<NumericPredictor> numericPredictors = regressionTable.getNumericPredictors();
		for(NumericPredictor numericPredictor : numericPredictors){
			Object value = ExpressionUtil.evaluate(numericPredictor.getName(), context);

			// "if the input value is missing, then the result evaluates to a missing value"
			if(value == null){
				return null;
			}

			result += numericPredictor.getCoefficient() * Math.pow(((Number)value).doubleValue(), numericPredictor.getExponent());
		}

		List<CategoricalPredictor> categoricalPredictors = regressionTable.getCategoricalPredictors();
		for(CategoricalPredictor categoricalPredictor : categoricalPredictors){
			Object value = ExpressionUtil.evaluate(categoricalPredictor.getName(), context);

			// "if the input value is missing, then the product is ignored"
			if(value == null){
				continue;
			}

			boolean equals = ParameterUtil.equals(value, categoricalPredictor.getValue());

			result += categoricalPredictor.getCoefficient() * (equals ? 1d : 0d);
		}

		List<PredictorTerm> predictorTerms = regressionTable.getPredictorTerms();
		for(PredictorTerm predictorTerm : predictorTerms){
			double product = predictorTerm.getCoefficient();

			List<FieldRef> fieldRefs = predictorTerm.getFieldRefs();
			if(fieldRefs.size() < 1){
				throw new InvalidFeatureException(predictorTerm);
			}

			for(FieldRef fieldRef : fieldRefs){
				Object value = ExpressionUtil.evaluateFieldRef(fieldRef, context);

				// "if the input value is missing, then the result evaluates to a missing value"
				if(value == null){
					return null;
				}

				product *= ((Number)value).doubleValue();
			}

			result += product;
		}

		return Double.valueOf(result);
	}

	static
	private Double normalizeRegressionResult(RegressionModel regressionModel, Double value){
		RegressionNormalizationMethodType regressionNormalizationMethod = regressionModel.getNormalizationMethod();

		switch(regressionNormalizationMethod){
			case NONE:
				return value;
			case SOFTMAX:
			case LOGIT:
				return 1d / (1d + Math.exp(-value));
			case EXP:
				return Math.exp(value);
			default:
				throw new UnsupportedFeatureException(regressionModel, regressionNormalizationMethod);
		}
	}

	static
	private Double normalizeClassificationResult(RegressionModel regressionModel, Double value, Double sumExp){
		RegressionNormalizationMethodType regressionNormalizationMethod = regressionModel.getNormalizationMethod();

		switch(regressionNormalizationMethod){
			case NONE:
				return value;
			case SOFTMAX:
				return Math.exp(value) / sumExp;
			case LOGIT:
				return 1d / (1d + Math.exp(-value));
			case CLOGLOG:
				return 1d - Math.exp(-Math.exp(value));
			case LOGLOG:
				return Math.exp(-Math.exp(-value));
			default:
				throw new UnsupportedFeatureException(regressionModel, regressionNormalizationMethod);
		}
	}
}