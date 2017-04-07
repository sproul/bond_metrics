package bondmetrics;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class Markup {
	public enum Rating {
            AAA,
            AAplus,
            AA,
            AAminus,
            Aplus,
            A,
            Aminus,
            BBBplus_or_below,
            NONE
    }

	public enum Type {
        MUNI,
        AGENCY,
        TREASURY,
        DTC_PRIMARY,
        DTC_SECONDARY
    }
	
	static class Schedule {
		class Schedule_dimension {
			boolean is_wildcard;
			Double greater_than_or_equal_threshold;

			public String make_key_component(String dimension_value) {
				if (this.is_wildcard) {
					return Markup.WILD_CARD;
				}
				if (this.greater_than_or_equal_threshold != null) {
					double dim_value = Double.parseDouble(dimension_value);
					if (dim_value > this.greater_than_or_equal_threshold) {
						
						String threshold_string = this.greater_than_or_equal_threshold.toString();
						threshold_string = threshold_string.replaceAll("\\.0$",  "");
						return threshold_string;
					}
				}
				return dimension_value;
			}
			public String toString() {
				StringBuffer sb = new StringBuffer();
				sb.append("Schedule_dimension(");
				if (this.is_wildcard) {
					sb.append("*");
				}
				else if (greater_than_or_equal_threshold != null) {
					sb.append(greater_than_or_equal_threshold)
					.append("+");
				}
				else {
					sb.append("-");
				}
				sb.append(")");
				return sb.toString();
			}
		}

		boolean expand_ranges = true;
		// for now max is 2; 0th is columns, 1rst dim is rows
		ArrayList<Schedule_dimension> dims = new ArrayList<Markup.Schedule.Schedule_dimension>();
		HashMap<String, Double> markups = new HashMap<String, Double>();
		ArrayList<String> col_headers = null;
		ArrayList<String> row_headers = new ArrayList<String>();
		ArrayList<Integer> data_col_duplication_coefficients = new ArrayList<Integer>(); 
		ArrayList<Integer> data_row_duplication_coefficients = new ArrayList<Integer>(); 
		
		public Schedule(InputStream csvInputStream) {
			Reader reader = new InputStreamReader(csvInputStream);
			BufferedReader csvInput = new BufferedReader(reader);
            this.dims.add(new Schedule_dimension()); // columns 
            this.dims.add(new Schedule_dimension()); // rows 
            try {
                Double row_greater_or_equal_to = null;
                int dataRowIndex = -1;
				while (csvInput.ready()) {
					String line = csvInput.readLine().replaceAll("[ \t]", "");
					if (line.matches(".*['\"].*")) {
						throw new RuntimeException("no quotes allowed in csv input: " + line);
					}
					if (this.col_headers == null) {
                        String line_no_row_header = line.replaceAll("^[^,]+,", "");      // col header for the row headers is irrelevant
						this.col_headers = stringArrayToArrayList(line_no_row_header.split(","));
                        Double col_greater_or_equal_to = null;
						for (int x = 0; x < this.col_headers.size(); x++) {
							String col_header = this.col_headers.get(x);
							if (col_header.equals(Markup.WILD_CARD)) {
								if (this.col_headers.size() > 1) {
									throw new RuntimeException("> 1 col header not allowed w/ wildcard:" + line);
								}
								this.dims.get(0).is_wildcard = true;
							}
							else if (col_header.matches("\\d+\\+$")) {
								col_header = col_header.replaceAll("\\+$", "");
								this.col_headers.set(x, col_header);
                                if (col_greater_or_equal_to != null) {
									throw new RuntimeException("can only be one greater_or_equal_to value for dimension 1 (cols):" + line);
								}
								col_greater_or_equal_to = Double.parseDouble(col_header);
								this.dims.get(0).greater_than_or_equal_threshold = col_greater_or_equal_to;
							}
							if (!this.expand_ranges) {
								data_col_duplication_coefficients.add(0);
							}   
							else {
								int column_duplication_coefficient = this.resolve_duplication_coefficient(col_header);
								data_col_duplication_coefficients.add(column_duplication_coefficient);
								if (column_duplication_coefficient > 0) {
									col_header = col_header.replaceAll("-.*", "");
									this.col_headers.set(x, col_header);
								}
							}
                        }
                    }
                    else {
                        String row_header = line.replaceAll(",.*", "");
                        if (row_header.equals(Markup.WILD_CARD)) {
                            if (this.row_headers.size() > 1) {
                                throw new RuntimeException("> 1 header is not allowed w/ wildcard:" + line);
                            }
                            this.dims.get(1).is_wildcard = true;
                        }
                        else if (row_header.matches("\\d+\\+$")) {
                            row_header = row_header.replaceAll("\\+$", "");
							if (row_greater_or_equal_to != null) {
                                throw new RuntimeException("can only be one greater_or_equal_to value for dimension 1 (rows):" + line);
                            }
                            row_greater_or_equal_to = Double.parseDouble(row_header);
                            this.dims.get(1).greater_than_or_equal_threshold = row_greater_or_equal_to;
                        }
                        if (!this.expand_ranges) {
                            this.data_row_duplication_coefficients.add(0);
                        }
                        else {
                            int row_duplication_coefficient = this.resolve_duplication_coefficient(row_header);
                            this.data_row_duplication_coefficients.add(row_duplication_coefficient);
                            if (row_duplication_coefficient > 0) {
                                row_header = row_header.replaceAll("-.*", "");
                            }
                        }
                        this.row_headers.add(row_header);
                        String line_no_row_header = line.replaceAll("^[^,]+,", "");        // already processed row header in the line above
                        
                        String[] row_data = line_no_row_header.split(",");
                        if (row_data.length != col_headers.size()) {
                            throw new RuntimeException("since there are " + col_headers.size() + " column headers, there should be the same number of data columns; but I see " + row_data.length + " columns in " + line);
                        }
                        for (int rangeExtendedRow = 0; rangeExtendedRow <= this.data_row_duplication_coefficients.get(dataRowIndex); rangeExtendedRow++) {
                        	for(int colIndex = 0; colIndex < row_data.length; colIndex++) {
                        		String col_header = this.col_headers.get(colIndex);
                        		Double datum = Double.parseDouble(row_data[colIndex]);
                        		// if there is no range in the headers, then this loop executes just once
                                for (int rangeExtendedCol = 0; rangeExtendedCol <= this.data_col_duplication_coefficients.get(colIndex); rangeExtendedCol++) {
                                    String[] dimension_keys = this.make_possibly_range_extended_key(rangeExtendedCol, rangeExtendedRow, col_header, row_header);
                                    this.set_markup(dimension_keys, datum);
                                }
                            }
                        }
                    }
					dataRowIndex++;
				}
			} catch (IOException e) {
				throw new RuntimeException("trouble reading csvInput: ", e);
			}
		}
        private String[] make_possibly_range_extended_key(int rangeExtendedCol, int rangeExtendedRow, String col_header, String row_header) {
            if (rangeExtendedRow > 0) {
                int base = Integer.parseInt(row_header);
                row_header = "" + (base + rangeExtendedRow);
            }
            if (rangeExtendedCol > 0) {
                int base = Integer.parseInt(col_header);
                col_header = "" + (base + rangeExtendedCol);
            }
            String[] composite_key = { this.dims.get(0).make_key_component(col_header), this.dims.get(1).make_key_component(row_header) };
            return composite_key;
        }
		private int resolve_duplication_coefficient(String header) {
			if (!header.matches("^\\d+-\\d+$")) {
				return 0;		// normal case, this is not a range
			}
			int range_start = Integer.parseInt(header.replaceAll("-.*", ""));
			int range_end  = Integer.parseInt(header.replaceAll(".*-", ""));
			int expand_to_this_number_of_columns = range_end - range_start;
			return expand_to_this_number_of_columns;
		}
		
		private ArrayList<String> stringArrayToArrayList(String[] strings) {
	        ArrayList<String> a = new ArrayList<String>();
	        for (String string : strings) {
				a.add(string);
			}
			return a;
		}
		public void add_dim(Schedule_dimension dim) {
			this.dims.add(dim);
		}
		public String make_key(String[] dimension_keys) {
			if (dimension_keys == null) {
				throw new RuntimeException("null dimension keys");
			}
			if (this.dims == null) {
				throw new RuntimeException("null dims");
			}
			if (dimension_keys.length != this.dims.size()) {
				throw new RuntimeException("key dimension mismatch -- expected " + this.dims.size() + " dimensions");
			}
			if (this.dims.size()==0) {
				throw new RuntimeException("no dims defined");
			}
			StringBuffer sb = new StringBuffer();
			for (int j = 0; j < this.dims.size(); j++) {
				sb.append(this.dims.get(j).make_key_component(dimension_keys[j]));
			}
			return sb.toString();
		}

		public void set_markup(String[] dimension_keys, Double val) {
			String key = this.make_key(dimension_keys);
			this.markups.put(key, val);
		}
    
        public double get_markup(String[] dimension_keys, double price) {
            String key = this.make_key(dimension_keys);
            Double val = this.markups.get(key);
            if (val == null) {
            	throw new RuntimeException("no markup set for key " + key);
            }
			return val + price;
		}

    	public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("Schedule(")
                .append("dims=")
                .append(dims)
                .append(",col_headers=")
                .append(col_headers)
                .append(",row_headers=")
                .append(row_headers)
                .append(",markups=")
                .append(markups)
                .append(",data_col_duplication_coefficients=")
                .append(data_col_duplication_coefficients)
                .append(",data_row_duplication_coefficients=")
                .append(data_row_duplication_coefficients);
            return sb.toString();
        }
	}

	private static final String WILD_CARD = "*";
	
	protected Date maturity;
	protected Rating rating;
	protected double price;
	protected Type type;
    static HashMap<Markup.Type, Schedule> type_to_schedule = new HashMap<Markup.Type, Schedule>();

    /**
     * Given a price, bond type, and a date of maturity, return the price including markup.
     */
	public static double calculate(double price, Type type, Date maturity) {
        Markup m = new Markup(price, type, maturity);
        return m.calculate();
	}

    /**
     * Given a price, bond type, date of maturity, and a rating, return the price including markup.
     */
	public static double calculate(double price, Type type, Date maturity, Rating rating) {
        Markup m = new Markup(price, type, maturity, rating);
        return m.calculate();
    }

    /**
     * Given a price, bond type, date of maturity, and a rating, return the price including markup.
     */
	public static double calculate(double price, Type type, Date maturity, String rating_string) {
    	Markup m = new Markup(price, type, maturity, rating_string);
    	return m.calculate();
    }

    /**
     * Given a price, bond type, and a date of maturity, construct a Markup object.
     */
	public Markup(double price, Type type, Date maturity) {
		this(price, type, maturity, Rating.NONE);
	}

    /**
     * Given a price, bond type, date of maturity, and a rating, construct a Markup object.
     */
	public Markup(double price, Type type, Date maturity, Rating rating) {
        this.price = price;
        this.type = type;
        this.maturity = maturity;
        this.rating = rating;
    }

    /**
     * Given a price, bond type, date of maturity, and a rating, construct a Markup object.
     */
	public Markup(double price, Type type, Date maturity, String rating_string) {
    	this(price, type, maturity, (rating_string == null ? null : Rating.valueOf(rating_string)));
    }
    
    public String toString() {
    	StringBuffer sb = new StringBuffer();
    	sb.append("Markup(")
    	.append("price=")
    	.append(price)
    	.append(",type=")
    	.append(type.toString())
    	.append(",maturity=")
    	.append(maturity);
    	if (rating != null) {
    		sb.append(", rating=")
    		.append(rating.toString()	);
    	}
    	sb.append(")");
    	return sb.toString();
    }

	/**
     * Calculate an appropriate markup for this bond.
     * 
     * Adding the value returned by this routine to this.price will give us the price visible to the user.
     */
	public double calculate() {
        Schedule schedule = Markup.type_to_schedule.get(this.type);
        long years_to_maturity = calculate_years_to_maturity();
        String[] dimension_keys = null;
        if (rating != null) {
        	String[] z = { "" + years_to_maturity, rating.toString() };
        	dimension_keys = z;
        }
        else if (schedule.dims.get(0).is_wildcard) {
        	String[] z = { WILD_CARD, "" + years_to_maturity };
        	dimension_keys = z;
        }
        else if (schedule.dims.get(1).is_wildcard) {
        	String[] z = { "" + years_to_maturity, WILD_CARD };
        	dimension_keys = z;
        }
        else {
        	throw new RuntimeException("lsdfjk");
        }
        return schedule.get_markup(dimension_keys, this.price);
    }

	protected long calculate_years_to_maturity() {
		long t_now = new Date().getTime();
        long t_maturity = this.maturity.getTime();
        long years_to_maturity = (t_maturity - t_now) / (24 * 365 * 60 * 60 * 1000L);
		return years_to_maturity + 1;	// rounding up, i.e., if we are three days to maturity, we consider that to be 1 year.
	}
	
    /**
     * Load (or reload) the markup schedule for bonds of the given type.
     *
     * <p>This routine expects to find a csv file on the class path whose name consists of
     *
     * <ul>
     * <li>markup_</li>
     * <li>name of the type</li>
     * <li>.csv</li>
     * </ul>
     *
     * <p>So, for example, if we call load_markup_schedule(Markup.Type.MUNI), then the code will
     * expect to find a file markup_MUNI.csv on the class path.
     *
     * <p>The csv is expected to be a grid giving the markup for each combination of rating and
     * years-to-maturity.
     *
     * <p><b>csv contents example #1:</b>
     * <pre>
     * ratings,1,2+
     * AAA,0.45,0.60
     * AA,0.55,0.75
     * </pre>
     *
     * <p>In this example:
     * AAA bonds with 1 year to maturity would be marked up $0.45.
     * AAA bonds with 2 or more years to maturity would be marked up $0.60.
     * AA bonds with 1 year to maturity would be marked up $0.55.
     * AA bonds with 2 or more years to maturity would be marked up $0.75.
     *
     * <p><b>csv contents example #2:</b>
     * <pre>
     * ratings,1,2+
     * *,0.45,0.60
     * </pre>
     *
     * <p>In this example, ratings are ignored and the only thing that counts is the years to maturity:
     * Bonds with 1 year to maturity would be marked up $0.45.
     * Bonds with 2 or more years to maturity would be marked up $0.60.
     *
     * <p><b>csv contents example #3:</b>
     * <pre>
     * ratings,*
     * *,0.45
     * </pre>
     *
     * In this example, a flat markup is applied:
     * All bonds are marked up $0.45.
     *
     */
	static public void load_markup_schedule(Type type) {
		String csvFn = "markup_" + type + ".csv";
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			throw new RuntimeException("cannot get classloader instance");
		}
		InputStream in = cl.getResourceAsStream(csvFn);
		if (in == null) {
			throw new RuntimeException("cannot find " + csvFn + " on the classpath");
		}
		load_markup_schedule(type, in);
    }
    
    /**
     * Load (or reload) the markup schedule for securities of the given type.
     *
     * <p>This routine expects its string argument to contain lines organized csv-style, indicating a schedule
     * of markups.
     *
     * <p>The csv is expected to be a grid giving the markup for each combination of rating and
     * years-to-maturity.
     *
     * <p><b>csv contents example #1:</b>
     * <pre>
     * ratings,1,2+
     * AAA,0.45,0.60
     * AA,0.55,0.75
     * </pre>
     *
     * <p>In this example:
     * AAA bonds with 1 year to maturity would be marked up $0.45.
     * AAA bonds with 2 or more years to maturity would be marked up $0.60.
     * AA bonds with 1 year to maturity would be marked up $0.55.
     * AA bonds with 2 or more years to maturity would be marked up $0.75.
     *
     * <p><b>csv contents example #2:</b>
     * <pre>
     * ratings,1,2+
     * *,0.45,0.60
     * </pre>
     *
     * <p>In this example, ratings are ignored and the only thing that counts is the years to maturity:
     * Bonds with 1 year to maturity would be marked up $0.45.
     * Bonds with 2 or more years to maturity would be marked up $0.60.
     *
     * <p><b>csv contents example #3:</b>
     * <pre>
     * ratings,*
     * *,0.45
     * </pre>
     *
     * In this example, a flat markup is applied:
     * All bonds are marked up $0.45.
     *
     */
    static public void load_markup_schedule(Type type, String s) {
    	InputStream is = new ByteArrayInputStream(s.getBytes());
    	load_markup_schedule(type, is);
	}

    /*
     * Load a markup schedule from an input stream.
     */
    static public void load_markup_schedule(Type type, InputStream in) {
        type_to_schedule.put(type, new Schedule(in));
	}

	/*
     * Load (or reload) all markup schedules.
     */
	static public void load_markup_all_schedules() {
        for (Type type : Type.values()) {
            load_markup_schedule(type);
        }
    }
	
	static public void main(String[] argv) {
		Markup.load_markup_all_schedules();
		Markup.Type type = Markup.Type.valueOf(argv[0]);
		double price = Double.parseDouble(argv[1]);
		int maturity_month = Integer.parseInt(argv[2]);
		int maturity_day = Integer.parseInt(argv[3]);
		int maturity_year = Integer.parseInt(argv[4]);
		@SuppressWarnings("deprecation")
		Date maturity = new Date(maturity_year, maturity_month, maturity_day);
		double markedUpPrice;
		if (type == Markup.Type.MUNI) {
			Markup.Rating rating = Markup.Rating.valueOf(argv[5]);
			markedUpPrice = Markup.calculate(price, type, maturity, rating);
		}
		else {
			markedUpPrice = Markup.calculate(price, type, maturity);
		}
		System.out.println("" + markedUpPrice);
	}
}
