module Synthea
  module Patient
    class Manager

      attr_reader :patient
        
      def initialize
        @patient = Record.new
      end

      def evaluate(date)
        return if @patient.expired
        process(Synthea::Events::Core::Death, date) if Synthea::Likelihood::Death.evaluate(self, date)
      end

      def age(now)
        unless @patient.expired
          ((now.to_i - @patient.birthdate)/1.year).floor
        else
          ((@patient.deathdate.to_i - @patient.birthdate)/1.year).floor
        end
      end

      def process(event, date)
        event.new(date).apply(self)
      end

    end
  end
end